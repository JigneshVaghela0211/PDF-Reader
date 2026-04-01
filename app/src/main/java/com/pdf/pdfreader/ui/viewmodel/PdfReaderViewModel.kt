package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.utiles.PdfPageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.collections.emptyList

import com.pdf.pdfreader.domain.model.PdfAnnotation
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.ui.components.AnnotationTool
import androidx.compose.ui.graphics.Color

data class PdfReaderUiState(
    val filePath: String = "",
    val fileName: String = "",
    val isPasswordProtected: Boolean = false,
    val isPasswordPromptVisible: Boolean = false,
    val password: String = "",
    val isPasswordCorrect: Boolean = true,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val zoomLevel: Float = 1f,
    val reloadTrigger: Int = 0,
    val isEditMode: Boolean = false,
    val currentTool: AnnotationTool = AnnotationTool.PEN,
    val currentColor: Color = Color.Red,
    val currentStrokeWidth: Float = 5f,
    val annotations: List<PdfAnnotation> = emptyList(),
    val isBookmarked: Boolean = false,
    val bookmarks: List<com.pdf.pdfreader.data.local.BookmarkEntity> = emptyList(),
    val isNightMode: Boolean = false
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    application: Application,
    private val pdfRepository: com.pdf.pdfreader.domain.repository.PdfRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState = _uiState.asStateFlow()

    private var pdfRenderer: PdfPageRenderer? = null
    
    // Limits PdfRenderer access to a single thread to prevent concurrent usage errors
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4 // 25% of memory for bitmaps

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun initialize(path: String, password: String? = null) {
        viewModelScope.launch {
            val name = File(path).name
            var initialPage = 0
            try {
                val existing = pdfRepository.getPdfFiles().first().find { it.path == path }
                if (existing != null) initialPage = existing.lastOpenedPage
            } catch (e: Exception) { e.printStackTrace() }

            _uiState.update { it.copy(filePath = path, fileName = name, isLoading = true, errorMessage = null, currentPage = initialPage) }
            
            observeBookmarks(path)
            
            withContext(pdfDispatcher) {
                try {
                    pdfRenderer?.close()
                    pdfRenderer = PdfPageRenderer(getApplication(), path, password)
                    
                    val pages = pdfRenderer?.pageCount ?: 0
                    if (pages > 0) {
                        _uiState.update { 
                            it.copy(
                                totalPages = pages, 
                                isLoading = false, 
                                isPasswordProtected = false, 
                                isPasswordCorrect = true, 
                                isPasswordPromptVisible = false,
                                errorMessage = null
                            ) 
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load PDF or PDF is empty") }
                    }
                } catch (e: SecurityException) {
                    _uiState.update { 
                        it.copy(
                            isPasswordProtected = true, 
                            isPasswordPromptVisible = true, 
                            isLoading = false, 
                            isPasswordCorrect = password == null 
                        ) 
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        _uiState.update { 
                            it.copy(
                                isPasswordProtected = true, 
                                isPasswordPromptVisible = true, 
                                isLoading = false, 
                                isPasswordCorrect = password == null 
                            ) 
                        }
                    } else {
                        _uiState.update { it.copy(errorMessage = e.localizedMessage, isLoading = false) }
                    }
                }
            }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, width: Int): Bitmap? {
        val cached = bitmapCache.get(pageIndex)
        if (cached != null) return cached
        
        return withContext(pdfDispatcher) {
            val doubleCached = bitmapCache.get(pageIndex)
            if (doubleCached != null) return@withContext doubleCached
            
            val bitmap = pdfRenderer?.renderPage(pageIndex, width, uiState.value.isNightMode)
            if (bitmap != null) {
                bitmapCache.put(pageIndex, bitmap)
            }
            bitmap
        }
    }

    fun addAnnotation(annotation: PdfAnnotation) {
        _uiState.update { it.copy(annotations = it.annotations + annotation) }
    }

    fun removeAnnotation(id: String) {
        _uiState.update { state -> 
            state.copy(annotations = state.annotations.filter { it.id != id }) 
        }
    }

    fun updateAnnotation(annotation: PdfAnnotation) {
        _uiState.update { state ->
            state.copy(annotations = state.annotations.map { if (it.id == annotation.id) annotation else it })
        }
    }

    fun saveAnnotationsToPdf(viewWidth: Int) {
        val path = uiState.value.filePath
        if (path.isEmpty() || uiState.value.annotations.isEmpty()) return
        val annotationsToSave = uiState.value.annotations.toList()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val file = java.io.File(path)
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                    val annotationsByPage = annotationsToSave.groupBy { it.pageIndex }

                    for ((pageIndex, pageAnns) in annotationsByPage) {
                        val page = document.getPage(pageIndex)
                        val cropBox = page.cropBox
                        val pdfWidth = cropBox.width
                        val pdfHeight = cropBox.height

                        val scaleX = pdfWidth / viewWidth.toFloat()
                        val scaleY = scaleX

                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { contentStream ->
                            for (ann in pageAnns) {
                                when (ann) {
                                    is PdfAnnotation.Path -> {
                                        if (ann.points.size < 2) continue
                                        
                                        val c = ann.color
                                        contentStream.setStrokingColor(
                                            (c.red * 255).toInt(),
                                            (c.green * 255).toInt(),
                                            (c.blue * 255).toInt()
                                        )
                                        
                                        contentStream.setLineWidth(ann.strokeWidth * scaleX)
                                        val graphicsState = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                                        graphicsState.strokingAlphaConstant = if (ann.isHighlighter) 0.5f else 1.0f
                                        contentStream.setGraphicsStateParameters(graphicsState)

                                        val startP = ann.points.first()
                                        contentStream.moveTo(startP.x * scaleX, pdfHeight - (startP.y * scaleY))
                                        for (i in 1 until ann.points.size) {
                                            val p = ann.points[i]
                                            contentStream.lineTo(p.x * scaleX, pdfHeight - (p.y * scaleY))
                                        }
                                        contentStream.stroke()
                                    }
                                    is PdfAnnotation.TextNote -> {
                                        contentStream.beginText()
                                        val c = ann.color
                                        contentStream.setNonStrokingColor(
                                            (c.red * 255).toInt(),
                                            (c.green * 255).toInt(),
                                            (c.blue * 255).toInt()
                                        )
                                        contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, ann.fontSize * scaleX)
                                        contentStream.newLineAtOffset(ann.position.x * scaleX, pdfHeight - (ann.position.y * scaleY) - (ann.fontSize * scaleX))
                                        contentStream.showText(ann.text)
                                        contentStream.endText()
                                    }
                                }
                            }
                        }
                    }
                    
                    document.save(file)
                }
                
                withContext(pdfDispatcher) {
                    pdfRenderer?.close()
                    bitmapCache.evictAll()
                    pdfRenderer = com.pdf.pdfreader.utiles.PdfPageRenderer(getApplication(), path, uiState.value.password)
                }

                _uiState.update { it.copy(annotations = emptyList<PdfAnnotation>(), reloadTrigger = it.reloadTrigger + 1) }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false, isEditMode = false) }
            }
        }
    }

    fun setEditMode(isEditMode: Boolean) {
        _uiState.update { it.copy(isEditMode = isEditMode) }
    }

    fun setAnnotationTool(tool: AnnotationTool) {
        _uiState.update { it.copy(currentTool = tool) }
    }

    fun setAnnotationColor(color: Color) {
        _uiState.update { it.copy(currentColor = color) }
    }

    fun setAnnotationStrokeWidth(width: Float) {
        _uiState.update { it.copy(currentStrokeWidth = width) }
    }

    suspend fun getSearchHighlights(pageIndex: Int, query: String, viewWidth: Int, viewHeight: Int): List<androidx.compose.ui.geometry.Rect> {
        val path = uiState.value.filePath
        if (path.isEmpty() || query.isEmpty()) return emptyList<androidx.compose.ui.geometry.Rect>()

        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext emptyList<androidx.compose.ui.geometry.Rect>()
            
            val highlights = mutableListOf<androidx.compose.ui.geometry.Rect>()
            
            try {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                    if (document.isEncrypted) return@use emptyList<androidx.compose.ui.geometry.Rect>()

                    val page = document.getPage(pageIndex)
                    val cropBox = page.cropBox
                    val pdfWidth = cropBox.width
                    val pdfHeight = cropBox.height
                    
                    val scaleX = viewWidth / pdfWidth
                    val scaleY = viewHeight / pdfHeight

                    val stripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                        override fun writeString(text: String, textPositions: List<com.tom_roush.pdfbox.text.TextPosition>) {
                            val lowerText = text.lowercase()
                            val lowerQuery = query.lowercase()
                            
                            var startIndex = lowerText.indexOf(lowerQuery)
                            while (startIndex >= 0) {
                                val endIndex = startIndex + lowerQuery.length
                                var minX = Float.MAX_VALUE
                                var minY = Float.MAX_VALUE
                                var maxX = Float.MIN_VALUE
                                var maxY = Float.MIN_VALUE
                                
                                for (i in startIndex until endIndex) {
                                    if (i < textPositions.size) {
                                        val pos = textPositions[i]
                                        val x = pos.xDirAdj
                                        val y = pos.yDirAdj - pos.heightDir
                                        val w = pos.widthDirAdj
                                        val h = pos.heightDir
                                        
                                        if (x < minX) minX = x
                                        if (y < minY) minY = y
                                        if (x + w > maxX) maxX = x + w
                                        if (y + h > maxY) maxY = y + h
                                    }
                                }
                                
                                if (minX < Float.MAX_VALUE) {
                                    val mappedLeft = minX * scaleX
                                    val mappedTop = minY * scaleY
                                    val mappedRight = maxX * scaleX
                                    val mappedBottom = maxY * scaleY
                                    
                                    highlights.add(androidx.compose.ui.geometry.Rect(mappedLeft, mappedTop, mappedRight, mappedBottom))
                                }
                                
                                startIndex = lowerText.indexOf(lowerQuery, startIndex + 1)
                            }
                        }
                    }
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(document) 
                }
            } catch (e: Exception) {
            }
            highlights
        }
    }

    fun submitPassword(password: String) {
        val path = uiState.value.filePath
        if (path.isNotEmpty()) {
            _uiState.update { it.copy(password = password, isLoading = true) }
            initialize(path, password)
        }
    }

    fun onPasswordChange(p: String) {
        _uiState.update { it.copy(password = p) }
    }

    fun updateCurrentPage(page: Int) {
        if (page != _uiState.value.currentPage) {
            _uiState.update { state -> 
                state.copy(
                    currentPage = page,
                    isBookmarked = state.bookmarks.any { it.pageIndex == page }
                ) 
            }
        }
    }

    private fun observeBookmarks(path: String) {
        viewModelScope.launch {
            pdfRepository.getBookmarksForPdf(path).collect { bookmarks ->
                _uiState.update { state ->
                    state.copy(
                        bookmarks = bookmarks,
                        isBookmarked = bookmarks.any { it.pageIndex == state.currentPage }
                    )
                }
            }
        }
    }

    fun toggleBookmark() {
        val path = uiState.value.filePath
        val page = uiState.value.currentPage
        if (path.isEmpty()) return

        viewModelScope.launch {
            if (uiState.value.isBookmarked) {
                pdfRepository.removeBookmark(path, page)
            } else {
                pdfRepository.addBookmark(path, page)
            }
        }
    }

    fun toggleNightMode() {
        val nextMode = !uiState.value.isNightMode
        _uiState.update { it.copy(isNightMode = nextMode, isLoading = true) }
        viewModelScope.launch {
            bitmapCache.evictAll()
            _uiState.update { it.copy(isLoading = false, reloadTrigger = it.reloadTrigger + 1) }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        val path = _uiState.value.filePath
        val page = _uiState.value.currentPage
        if (path.isNotEmpty() && page >= 0) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    pdfRepository.updateLastOpenedPage(path, page)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        pdfRenderer?.close()
        bitmapCache.evictAll()
    }
}
