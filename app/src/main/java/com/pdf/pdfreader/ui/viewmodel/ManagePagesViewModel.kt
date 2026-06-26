package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.domain.repository.PdfRepository
import com.pdf.pdfreader.utiles.PdfPageManager
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
    val saveProgress: Float = 0f,
    val hasModifications: Boolean = false,
    val reloadTrigger: Int = 0
)

@HiltViewModel
class ManagePagesViewModel @Inject constructor(
    application: Application,
    private val pdfRepository: PdfRepository,
    private val pageManager: PdfPageManager
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
                        _uiState.update { it.copy(totalPages = pages, isLoading = false, reloadTrigger = it.reloadTrigger + 1) }
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
                _uiState.update { it.copy(isSaving = false, selectedPages = emptySet(), hasModifications = true) }
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
                _uiState.update { it.copy(isSaving = false, selectedPages = emptySet(), hasModifications = true) }
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
                    val timestamp = System.currentTimeMillis()
                    val newFile = File(destFolder, "${baseName}_extracted_$timestamp.pdf")
                    
                    PDDocument.load(srcFile).use { srcDoc ->
                        PDDocument().use { newDoc ->
                            for (pageIndex in pagesToExtract) {
                                if (pageIndex in 0 until srcDoc.numberOfPages) {
                                    val page = srcDoc.getPage(pageIndex)
                                    // Since we are copying between documents, using importPage
                                    val imported = newDoc.importPage(page)
                                    imported.resources = page.resources
                                }
                            }
                            // Save newDoc BEFORE srcDoc is closed, as the streams are read lazily
                            newDoc.save(newFile)
                        }
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

    /**
     * Insert a blank page immediately after the highest selected page (or at the end of the
     * document when nothing is selected). Reuses [PdfPageManager]; the produced file replaces
     * the original in place, matching delete/rotate behaviour.
     */
    fun insertBlankPage(onComplete: (Boolean) -> Unit) {
        val sel = _uiState.value.selectedPages
        val insertAt = sel.maxOrNull()?.plus(1) ?: _uiState.value.totalPages
        runPageOp({ path -> pageManager.insertBlankPage(path, insertAt) }, onComplete)
    }

    /**
     * Duplicate every selected page; each copy is placed directly after its original. Implemented
     * as a reorder whose page list repeats the selected indices.
     */
    fun duplicateSelectedPages(onComplete: (Boolean) -> Unit) {
        val sel = _uiState.value.selectedPages
        if (sel.isEmpty()) { onComplete(false); return }
        val order = buildList {
            for (i in 0 until _uiState.value.totalPages) {
                add(i)
                if (i in sel) add(i)
            }
        }
        runPageOp({ path -> pageManager.reorder(path, order) }, onComplete)
    }

    /**
     * Persist a new page order. [newOrder] is the list of original 0-based page indices in their
     * desired sequence (a permutation of 0 until totalPages). No-op if it equals the identity order.
     */
    fun reorderPages(newOrder: List<Int>, onComplete: (Boolean) -> Unit) {
        val identity = (0 until _uiState.value.totalPages).toList()
        if (newOrder == identity) { onComplete(false); return }
        runPageOp({ path -> pageManager.reorder(path, newOrder) }, onComplete)
    }

    /** Reverse the entire page order (last page becomes first). No-op for 0/1-page documents. */
    fun reversePages(onComplete: (Boolean) -> Unit) {
        val n = _uiState.value.totalPages
        if (n <= 1) { onComplete(false); return }
        runPageOp({ path -> pageManager.reorder(path, (n - 1 downTo 0).toList()) }, onComplete)
    }

    /**
     * Run a [PdfPageManager] operation that yields a sibling file, then move it over the original
     * and reload. Keeps the in-place editing model the screen already uses for delete/rotate.
     */
    private fun runPageOp(
        op: suspend (path: String) -> PdfPageManager.PageResult?,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val path = _uiState.value.filePath
            val ok = try {
                val result = op(path)
                if (result == null) false
                else withContext(Dispatchers.IO) {
                    val produced = File(result.outputPath)
                    val original = File(path)
                    produced.exists() && produced.renameTo(original)
                }
            } catch (e: Exception) {
                Log.e("ManagePagesVM", "Page operation failed", e)
                false
            }
            thumbnailCache.evictAll()
            _uiState.update {
                it.copy(isSaving = false, selectedPages = emptySet(), hasModifications = ok || it.hasModifications)
            }
            if (ok) initialize(path)
            onComplete(ok)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        thumbnailCache.evictAll()
    }
}
