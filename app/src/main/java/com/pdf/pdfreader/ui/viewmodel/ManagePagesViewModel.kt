package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.domain.repository.PdfRepository
import com.pdf.pdfreader.utiles.PdfPageRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ManagePagesState(
    val filePath: String = "",
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val selectedPages: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveProgress: Float = 0f
)

@HiltViewModel
class ManagePagesViewModel @Inject constructor(
    application: Application,
    private val pdfRepository: PdfRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ManagePagesState())
    val uiState: StateFlow<ManagePagesState> = _uiState.asStateFlow()

    private var pdfRenderer: PdfPageRenderer? = null
    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Cache for thumbnails
    private val thumbnailCache = LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())

    fun initialize(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(filePath = path, isLoading = true, errorMessage = null) }
            withContext(pdfDispatcher) {
                try {
                    pdfRenderer?.close()
                    pdfRenderer = PdfPageRenderer(getApplication(), path, null)
                    val pages = pdfRenderer?.pageCount ?: 0
                    
                    if (pages > 0) {
                        _uiState.update { it.copy(totalPages = pages, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load PDF or PDF is empty") }
                    }
                } catch (e: Exception) {
                    Log.e("ManagePagesVM", "Failed to initialize", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
                }
            }
        }
    }

    suspend fun getThumbnail(pageIndex: Int, width: Int): Bitmap? {
        thumbnailCache.get(pageIndex)?.let {
            if (!it.isRecycled) return it
        }

        return withContext(pdfDispatcher) {
            try {
                val bitmap = pdfRenderer?.renderPage(pageIndex, width, false)
                if (bitmap != null) {
                    thumbnailCache.put(pageIndex, bitmap)
                }
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toggleSelection(pageIndex: Int) {
        val current = _uiState.value.selectedPages
        val newSet = if (current.contains(pageIndex)) current - pageIndex else current + pageIndex
        _uiState.update { it.copy(selectedPages = newSet) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPages = emptySet()) }
    }

    // ─── PDF Operations (PDFBox) ───────────────────────────────────────────────

    fun deleteSelectedPages(onComplete: (Boolean) -> Unit) {
        val pagesToDelete = _uiState.value.selectedPages.sortedDescending()
        if (pagesToDelete.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val path = _uiState.value.filePath
            try {
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    val tempFile = File(file.parent, "temp_${file.name}")
                    
                    PDDocument.load(file).use { doc ->
                        for (pageIndex in pagesToDelete) {
                            if (pageIndex in 0 until doc.numberOfPages) {
                                doc.removePage(pageIndex)
                            }
                        }
                        doc.save(tempFile)
                    }

                    if (tempFile.exists()) {
                        tempFile.renameTo(file)
                    }
                }
                
                thumbnailCache.evictAll()
                _uiState.update { it.copy(isSaving = false, selectedPages = emptySet()) }
                initialize(path)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("ManagePagesVM", "Failed to delete pages", e)
                _uiState.update { it.copy(isSaving = false) }
                onComplete(false)
            }
        }
    }

    fun rotateSelectedPages(degrees: Int, onComplete: (Boolean) -> Unit) {
        val pagesToRotate = _uiState.value.selectedPages
        if (pagesToRotate.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val path = _uiState.value.filePath
            try {
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    val tempFile = File(file.parent, "temp_${file.name}")
                    
                    PDDocument.load(file).use { doc ->
                        for (pageIndex in pagesToRotate) {
                            if (pageIndex in 0 until doc.numberOfPages) {
                                val page = doc.getPage(pageIndex)
                                page.rotation = (page.rotation + degrees) % 360
                            }
                        }
                        doc.save(tempFile)
                    }

                    if (tempFile.exists()) {
                        tempFile.renameTo(file)
                    }
                }
                
                pagesToRotate.forEach { thumbnailCache.remove(it) }
                _uiState.update { it.copy(isSaving = false, selectedPages = emptySet()) }
                initialize(path)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("ManagePagesVM", "Failed to rotate pages", e)
                _uiState.update { it.copy(isSaving = false) }
                onComplete(false)
            }
        }
    }

    fun extractSelectedPages(destFolder: String, onComplete: (String?) -> Unit) {
        val pagesToExtract = _uiState.value.selectedPages.sorted()
        if (pagesToExtract.isEmpty()) {
            onComplete(null)
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val sourcePath = _uiState.value.filePath
            
            try {
                val newFilePath = withContext(Dispatchers.IO) {
                    val srcFile = File(sourcePath)
                    val baseName = srcFile.nameWithoutExtension
                    val newFile = File(destFolder, "${baseName}_extracted.pdf")
                    
                    PDDocument().use { newDoc ->
                        PDDocument.load(srcFile).use { srcDoc ->
                            for (pageIndex in pagesToExtract) {
                                if (pageIndex in 0 until srcDoc.numberOfPages) {
                                    val page = srcDoc.getPage(pageIndex)
                                    // Since we are copying between documents, using importPage
                                    val imported = newDoc.importPage(page)
                                    imported.resources = page.resources
                                }
                            }
                        }
                        newDoc.save(newFile)
                    }
                    newFile.absolutePath
                }
                _uiState.update { it.copy(isSaving = false, selectedPages = emptySet()) }
                onComplete(newFilePath)
            } catch (e: Exception) {
                Log.e("ManagePagesVM", "Extract failed", e)
                _uiState.update { it.copy(isSaving = false) }
                onComplete(null)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        thumbnailCache.evictAll()
    }
}
