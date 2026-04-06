package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.pdf.pdfreader.data.local.PreferenceManager
import com.pdf.pdfreader.domain.model.BackgroundMode
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.domain.model.ResizeHandle
import com.pdf.pdfreader.domain.model.TextBlock
import com.pdf.pdfreader.domain.model.ViewSettings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.data.local.CommandSerializer
import com.pdf.pdfreader.domain.model.AnnotationCommand
import com.pdf.pdfreader.domain.model.PdfAnnotation
import com.pdf.pdfreader.domain.model.SerializableOffset
import com.pdf.pdfreader.domain.usecase.UndoRedoManager
import com.pdf.pdfreader.ui.components.AnnotationTool
import com.pdf.pdfreader.utiles.PdfExportManager
import com.pdf.pdfreader.utiles.PdfPageRenderer
import com.pdf.pdfreader.utiles.PdfTextBlockExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

// ─── Page Render State ──────────────────────────────────────────
sealed class PageRenderState {
    data object Loading : PageRenderState()
    data class Success(val bitmap: Bitmap, val renderedWidth: Int) : PageRenderState()
    data class Error(val message: String) : PageRenderState()
}

// ─── Search Match ───────────────────────────────────────────────
data class SearchMatch(
    val pageIndex: Int,
    val rect: Rect,
    val matchText: String
)

// ─── UI State ───────────────────────────────────────────────────
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
    // ─── View Settings ─────────────────────────────────────
    val viewSettings: ViewSettings = ViewSettings(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val totalMatchCount: Int = 0,
    // ─── Undo/Redo State ────────────────────────────────────
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    
    // ─── Selection State ────────────────────────────────────
    val selectedAnnotationId: String? = null,

    // ─── Text Edit State ────────────────────────────────────
    val textBlocks: Map<Int, List<TextBlock>> = emptyMap(),
    val editedTextBlocks: List<EditedTextBlock> = emptyList(),
    val selectedTextBlockId: String? = null,
    val isTextBlocksLoading: Boolean = false,

    // ─── Image Element State ────────────────────────────────
    val imageElements: List<ImageElement> = emptyList(),
    val selectedImageId: String? = null,

    // ─── Interaction State ───────────────────────────────────
    val isOverlayInteracting: Boolean = false,

    // ─── Export State ───────────────────────────────────────
    val isExporting: Boolean = false,
    val exportResult: String? = null
) {
    /** Convenience: true when background mode is INVERT */
    val isNightMode: Boolean get() = viewSettings.backgroundMode == BackgroundMode.INVERT

    /** True when any editable overlay exists (text edits or images) */
    val hasEditableOverlays: Boolean get() = editedTextBlocks.isNotEmpty() || imageElements.isNotEmpty()
}

sealed class ScrollEvent {
    data class ScrollToPage(val pageIndex: Int) : ScrollEvent()
}

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    application: Application,
    private val pdfRepository: com.pdf.pdfreader.domain.repository.PdfRepository,
    private val undoRedoManager: UndoRedoManager,
    private val preferenceManager: PreferenceManager,
    private val textBlockExtractor: PdfTextBlockExtractor,
    private val pdfExportManager: PdfExportManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PdfReaderVM"
        private val MAX_CACHE_SIZE_KB = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt().coerceAtLeast(80 * 1024)
        private const val MAX_RETRY_BEFORE_REINIT = 2
        private const val ZOOM_RERENDER_THRESHOLD = 1.5f
    }

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val _pageStates = MutableStateFlow<Map<Int, PageRenderState>>(emptyMap())
    val pageStates = _pageStates.asStateFlow()

    private val _scrollEvents = MutableSharedFlow<ScrollEvent>(extraBufferCapacity = 1)
    val scrollEvents = _scrollEvents.asSharedFlow()

    private var pdfRenderer: PdfPageRenderer? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_KB) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                val isInUse = _pageStates.value.values.any { 
                    it is PageRenderState.Success && it.bitmap === oldValue 
                }
                if (!isInUse) {
                    oldValue.recycle()
                }
            }
        }
    }

    private val activeRenderJobs = ConcurrentHashMap<Int, Job>()
    private val failureCounts = ConcurrentHashMap<Int, Int>()

    init {
        // Observe undo/redo state changes and sync to UI
        viewModelScope.launch {
            undoRedoManager.canUndo.collect { canUndo ->
                _uiState.update { it.copy(canUndo = canUndo) }
            }
        }
        viewModelScope.launch {
            undoRedoManager.canRedo.collect { canRedo ->
                _uiState.update { it.copy(canRedo = canRedo) }
            }
        }
    }

    // ─── Initialize ─────────────────────────────────────────────

    fun initialize(path: String, password: String? = null) {
        viewModelScope.launch {
            val name = File(path).name
            var initialPage = 0
            try {
                val existing = pdfRepository.getPdfFiles().first().find { it.path == path }
                if (existing != null) initialPage = existing.lastOpenedPage
            } catch (e: Exception) { e.printStackTrace() }

            // Load persisted view settings
            val savedSettings = preferenceManager.viewSettingsFlow.first()
            _uiState.update {
                it.copy(filePath = path, fileName = name, isLoading = true, errorMessage = null, currentPage = initialPage, viewSettings = savedSettings)
            }
            observeBookmarks(path)

            // Restore undo/redo state from DB
            restoreUndoRedoState(path)

            withContext(pdfDispatcher) {
                try {
                    pdfRenderer?.close()
                    pdfRenderer = PdfPageRenderer(getApplication(), path, password)
                    val pages = pdfRenderer?.pageCount ?: 0
                    Log.d(TAG, "Initialized renderer: pages=$pages, path=$path")

                    if (pages > 0) {
                        _uiState.update {
                            it.copy(totalPages = pages, isLoading = false, isPasswordProtected = false,
                                isPasswordCorrect = true, isPasswordPromptVisible = false, errorMessage = null)
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load PDF or PDF is empty") }
                    }
                } catch (e: SecurityException) {
                    _uiState.update { it.copy(isPasswordProtected = true, isPasswordPromptVisible = true,
                        isLoading = false, isPasswordCorrect = password == null) }
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        _uiState.update { it.copy(isPasswordProtected = true, isPasswordPromptVisible = true,
                            isLoading = false, isPasswordCorrect = password == null) }
                    } else {
                        Log.e(TAG, "Failed to initialize", e)
                        _uiState.update { it.copy(errorMessage = e.localizedMessage, isLoading = false) }
                    }
                }
            }
        }
    }

    /**
     * Called when the underlying PDF file was modified (e.g. by ManagePagesScreen).
     * Clears caches and forces a full reload of the existing file path.
     */
    fun refreshCurrentPdf() {
        val path = _uiState.value.filePath
        if (path.isNotEmpty()) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                clearBitmapCache()
                _pageStates.value = emptyMap()
                
                withContext(pdfDispatcher) {
                    try {
                        pdfRenderer?.close()
                        pdfRenderer = PdfPageRenderer(getApplication(), path, _uiState.value.password.takeIf { it.isNotEmpty() })
                        val pages = pdfRenderer?.pageCount ?: 0
                        Log.d(TAG, "Refreshed renderer: new pages=$pages")
                        
                        if (pages > 0) {
                            _uiState.update {
                                it.copy(totalPages = pages, isLoading = false, reloadTrigger = it.reloadTrigger + 1)
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load PDF or PDF is empty") }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to refresh", e)
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
                    }
                }
            }
        }
    }

    // ─── Undo/Redo State Restoration ────────────────────────────

    /**
     * Restore undo/redo state from Room DB on app restart.
     * Replays active commands to rebuild the annotation list.
     */
    private suspend fun restoreUndoRedoState(pdfPath: String) {
        try {
            val activeCommands = withContext(Dispatchers.IO) {
                undoRedoManager.restoreState(pdfPath)
            }

            if (activeCommands.isNotEmpty()) {
                // Rebuild annotations from active commands
                val restoredAnnotations = activeCommands.mapNotNull { command ->
                    commandToAnnotation(command)
                }

                _uiState.update { it.copy(annotations = restoredAnnotations) }
                Log.d(TAG, "Restored ${restoredAnnotations.size} annotations from ${activeCommands.size} commands")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore undo/redo state", e)
        }
    }

    // ─── Core Rendering (Per-Page Job Architecture) ─────────────

    fun requestPageRender(pageIndex: Int, width: Int) {
        if (width <= 0) return

        val cacheKey = "${pageIndex}_${width}"

        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            updatePageState(pageIndex, PageRenderState.Success(cached, width))
            return
        }

        val existingJob = activeRenderJobs[pageIndex]
        if (existingJob != null && existingJob.isActive) return

        val job = viewModelScope.launch {
            renderPageInternal(pageIndex, width)
        }
        activeRenderJobs[pageIndex] = job
    }

    fun retryPageRender(pageIndex: Int, width: Int) {
        Log.d(TAG, "retryPageRender: page=$pageIndex")
        activeRenderJobs.remove(pageIndex)?.cancel()
        updatePageState(pageIndex, PageRenderState.Loading)
        val job = viewModelScope.launch {
            renderPageInternal(pageIndex, width)
        }
        activeRenderJobs[pageIndex] = job
    }

    private suspend fun renderPageInternal(pageIndex: Int, width: Int, height: Int = 0) {
        val cacheKey = if (height > 0) "${pageIndex}_${width}x${height}" else "${pageIndex}_${width}"

        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            updatePageState(pageIndex, PageRenderState.Success(cached, width))
            return
        }

        val currentState = _pageStates.value[pageIndex]
        if (currentState !is PageRenderState.Success) {
            updatePageState(pageIndex, PageRenderState.Loading)
        }

        val bitmap: Bitmap? = try {
            withContext(pdfDispatcher) {
                if (!isActive) return@withContext null

                val renderer = pdfRenderer
                if (renderer == null || !renderer.isReady) {
                    Log.w(TAG, "Renderer not ready, attempting reinitialize")
                    reinitializeRendererOnDispatcher()
                }

                val r = pdfRenderer ?: return@withContext null

                if (height > 0) {
                    r.renderPage(pageIndex, width, height, _uiState.value.isNightMode)
                } else {
                    r.renderPage(pageIndex, width, _uiState.value.isNightMode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "renderPageInternal exception for page $pageIndex", e)
            null
        }

        if (!coroutineContext.isActive) {
            bitmap?.recycle()
            return
        }

        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
            updatePageState(pageIndex, PageRenderState.Success(bitmap, width))
            failureCounts.remove(pageIndex)
        } else {
            val failures = (failureCounts[pageIndex] ?: 0) + 1
            failureCounts[pageIndex] = failures
            Log.w(TAG, "Render failed for page $pageIndex (attempt $failures)")

            if (failures >= MAX_RETRY_BEFORE_REINIT) {
                Log.w(TAG, "Max failures reached, reinitializing renderer and retrying")
                failureCounts.remove(pageIndex)

                try {
                    withContext(pdfDispatcher) { reinitializeRendererOnDispatcher() }
                } catch (e: Exception) {
                    Log.e(TAG, "Reinitialize failed", e)
                }

                val retryBitmap = try {
                    withContext(pdfDispatcher) {
                        pdfRenderer?.renderPage(pageIndex, width, _uiState.value.isNightMode)
                    }
                } catch (e: Exception) { null }

                if (retryBitmap != null && coroutineContext.isActive) {
                    val retryKey = "${pageIndex}_${width}"
                    bitmapCache.put(retryKey, retryBitmap)
                    updatePageState(pageIndex, PageRenderState.Success(retryBitmap, width))
                } else {
                    retryBitmap?.recycle()
                    updatePageState(pageIndex, PageRenderState.Error("Rendering failed after recovery attempt"))
                }
            } else {
                updatePageState(pageIndex, PageRenderState.Error("Failed to render page"))
            }
        }
    }

    private fun reinitializeRendererOnDispatcher() {
        val path = _uiState.value.filePath
        val password = _uiState.value.password
        try {
            pdfRenderer?.close()
        } catch (e: Exception) { Log.w(TAG, "Error closing renderer for reinit", e) }
        pdfRenderer = PdfPageRenderer(getApplication(), path, if (password.isEmpty()) null else password)
        Log.d(TAG, "Renderer reinitialized: isReady=${pdfRenderer?.isReady}, pageCount=${pdfRenderer?.pageCount}")
    }

    fun requestHighResRender(pageIndex: Int, baseWidth: Int, zoomScale: Float) {
        if (zoomScale < ZOOM_RERENDER_THRESHOLD) return
        viewModelScope.launch {
            val dims = withContext(pdfDispatcher) { pdfRenderer?.getPageDimensions(pageIndex) } ?: return@launch
            val ratio = dims.second.toFloat() / dims.first.toFloat()
            val targetWidth = (baseWidth * zoomScale).toInt()
            val targetHeight = (targetWidth * ratio).toInt()
            val cacheKey = "${pageIndex}_${targetWidth}x${targetHeight}"
            val cached = bitmapCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                updatePageState(pageIndex, PageRenderState.Success(cached, targetWidth))
                return@launch
            }
            activeRenderJobs.remove(pageIndex)?.cancel()
            val job = viewModelScope.launch { renderPageInternal(pageIndex, targetWidth, targetHeight) }
            activeRenderJobs[pageIndex] = job
        }
    }

    fun cancelRenderingOutsideRange(visibleRange: IntRange) {
        activeRenderJobs.entries.removeAll { (pageIndex, job) ->
            if (pageIndex !in visibleRange) {
                job.cancel()
                true
            } else {
                false
            }
        }
        
        _pageStates.update { currentStates ->
            val keysToRemove = currentStates.keys.filter { it !in visibleRange }
            if (keysToRemove.isEmpty()) return@update currentStates
            val newStates = currentStates.toMutableMap()
            for (k in keysToRemove) {
                val state = newStates.remove(k)
                if (state is PageRenderState.Success) {
                    val cacheKeys = listOf("${k}_${state.renderedWidth}", "${k}_${state.renderedWidth}x")
                    if (!cacheKeys.any { key -> bitmapCache.get(key) === state.bitmap } && !state.bitmap.isRecycled) {
                        try { state.bitmap.recycle() } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
            newStates
        }
    }

    private fun updatePageState(pageIndex: Int, state: PageRenderState) {
        _pageStates.update { it.toMutableMap().apply { put(pageIndex, state) } }
    }

    // ─── In-PDF Search ──────────────────────────────────────────

    private var searchJob: Job? = null

    fun toggleSearch() {
        val isActive = !_uiState.value.isSearchActive
        _uiState.update {
            it.copy(isSearchActive = isActive,
                searchQuery = if (!isActive) "" else it.searchQuery,
                searchResults = if (!isActive) emptyList() else it.searchResults,
                currentMatchIndex = -1,
                totalMatchCount = if (!isActive) 0 else it.totalMatchCount)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), currentMatchIndex = -1, totalMatchCount = 0) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            searchInPdf(query)
        }
    }

    private suspend fun searchInPdf(query: String) {
        val path = _uiState.value.filePath
        if (path.isEmpty()) return
        val allMatches = mutableListOf<SearchMatch>()
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                    if (document.isEncrypted) return@use
                    for (pageIdx in 0 until document.numberOfPages) {
                        if (!isActive) return@withContext
                        val page = document.getPage(pageIdx)
                        val cropBox = page.cropBox
                        val pdfWidth = cropBox.width
                        val pdfHeight = cropBox.height
                        val stripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                            override fun writeString(text: String, textPositions: List<com.tom_roush.pdfbox.text.TextPosition>) {
                                val lowerText = text.lowercase()
                                val lowerQuery = query.lowercase()
                                var startIndex = lowerText.indexOf(lowerQuery)
                                while (startIndex >= 0) {
                                    val endIndex = startIndex + lowerQuery.length
                                    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
                                    for (i in startIndex until endIndex) {
                                        if (i < textPositions.size) {
                                            val pos = textPositions[i]
                                            val x = pos.xDirAdj; val y = pos.yDirAdj - pos.heightDir
                                            if (x < minX) minX = x; if (y < minY) minY = y
                                            if (x + pos.widthDirAdj > maxX) maxX = x + pos.widthDirAdj
                                            if (y + pos.heightDir > maxY) maxY = y + pos.heightDir
                                        }
                                    }
                                    if (minX < Float.MAX_VALUE) {
                                        allMatches.add(SearchMatch(pageIdx, Rect(minX / pdfWidth, minY / pdfHeight, maxX / pdfWidth, maxY / pdfHeight),
                                            text.substring(startIndex, (startIndex + lowerQuery.length).coerceAtMost(text.length))))
                                    }
                                    startIndex = lowerText.indexOf(lowerQuery, startIndex + 1)
                                }
                            }
                        }
                        stripper.startPage = pageIdx + 1; stripper.endPage = pageIdx + 1
                        stripper.getText(document)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Search failed", e) }
        }
        _uiState.update { it.copy(searchResults = allMatches, totalMatchCount = allMatches.size,
            currentMatchIndex = if (allMatches.isNotEmpty()) 0 else -1) }
        if (allMatches.isNotEmpty()) _scrollEvents.emit(ScrollEvent.ScrollToPage(allMatches[0].pageIndex))
    }

    fun navigateToNextMatch() {
        val results = _uiState.value.searchResults; if (results.isEmpty()) return
        val nextIndex = (_uiState.value.currentMatchIndex + 1) % results.size
        _uiState.update { it.copy(currentMatchIndex = nextIndex) }
        viewModelScope.launch { _scrollEvents.emit(ScrollEvent.ScrollToPage(results[nextIndex].pageIndex)) }
    }

    fun navigateToPreviousMatch() {
        val results = _uiState.value.searchResults; if (results.isEmpty()) return
        val prevIndex = if (_uiState.value.currentMatchIndex <= 0) results.size - 1 else _uiState.value.currentMatchIndex - 1
        _uiState.update { it.copy(currentMatchIndex = prevIndex) }
        viewModelScope.launch { _scrollEvents.emit(ScrollEvent.ScrollToPage(results[prevIndex].pageIndex)) }
    }

    fun getSearchHighlightsForPage(pageIndex: Int, viewWidth: Int, viewHeight: Int): List<Pair<Rect, Boolean>> {
        val state = _uiState.value
        if (!state.isSearchActive || state.searchResults.isEmpty()) return emptyList()
        return state.searchResults.filter { it.pageIndex == pageIndex }.map { match ->
            val globalIndex = state.searchResults.indexOf(match)
            Pair(Rect(match.rect.left * viewWidth, match.rect.top * viewHeight,
                match.rect.right * viewWidth, match.rect.bottom * viewHeight), globalIndex == state.currentMatchIndex)
        }
    }

    // ─── Annotations with Undo/Redo ─────────────────────────────

    /**
     * Add an annotation and register it as an undoable command.
     */
    fun addAnnotation(annotation: PdfAnnotation) {
        if (annotation is PdfAnnotation.TextNote) {
            // New TextNotes start as drafts. Do NOT create an undo command immediately.
            addDraftTextAnnotation(annotation)
            return
        }
        
        _uiState.update { it.copy(annotations = it.annotations + annotation) }

        // Create and execute command
        viewModelScope.launch {
            val command = annotationToCommand(annotation, _uiState.value.filePath)
            if (command != null) {
                undoRedoManager.execute(command)
            }
        }
    }

    /**
     * Adds an empty TextNote as a draft without recording an Undo command.
     */
    private fun addDraftTextAnnotation(annotation: PdfAnnotation.TextNote) {
        _uiState.update { it.copy(annotations = it.annotations + annotation) }
    }

    /**
     * Converts PdfAnnotation.TextNote to TextState
     */
    fun textNoteToState(note: PdfAnnotation.TextNote): AnnotationCommand.TextState {
        return AnnotationCommand.TextState(
            id = note.id,
            text = note.text,
            color = note.color.value.toLong(),
            fontSize = note.fontSize,
            positionX = note.position.x,
            positionY = note.position.y
        )
    }

    private fun stateToTextNote(state: AnnotationCommand.TextState, pageIndex: Int): PdfAnnotation.TextNote {
        return PdfAnnotation.TextNote(
            id = state.id,
            pageIndex = pageIndex,
            text = state.text,
            position = androidx.compose.ui.geometry.Offset(state.positionX, state.positionY),
            color = androidx.compose.ui.graphics.Color(state.color.toULong()),
            fontSize = state.fontSize
        )
    }

    /**
     * Commits a text annotation that has completed editing.
     * Generates a TextCommand if the note has valid text.
     */
    fun commitTextAnnotation(before: AnnotationCommand.TextState?, after: AnnotationCommand.TextState, pageIndex: Int) {
        // If blank text, do not create command, just remove draft if it was new
        if (after.text.isBlank()) {
            if (before == null) {
                // Was a draft, user typed nothing. Delete from view.
                _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != after.id }) }
            } else {
                // Edit made it blank, revert it to before
                val revertedNote = stateToTextNote(before, pageIndex)
                _uiState.update { s ->
                    s.copy(annotations = s.annotations.map { if (it.id == after.id) revertedNote else it })
                }
            }
            return
        }

        // Apply updated state to UI
        val newNote = stateToTextNote(after, pageIndex)
        _uiState.update { s ->
            val existing = s.annotations.find { it.id == after.id }
            if (existing != null) {
                s.copy(annotations = s.annotations.map { if (it.id == after.id) newNote else it })
            } else {
                s.copy(annotations = s.annotations + newNote)
            }
        }

        // Add to UndoManager
        viewModelScope.launch {
            val command = AnnotationCommand.TextCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = pageIndex,
                timestamp = System.currentTimeMillis(),
                before = before,
                after = after
            )
            undoRedoManager.execute(command)
        }
    }

    /**
     * Remove an annotation and register it as an undoable command.
     * Stores a snapshot of the removed annotation for redo.
     */
    fun removeAnnotation(id: String) {
        val removedAnnotation = _uiState.value.annotations.find { it.id == id }
        _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != id }) }

        if (removedAnnotation != null) {
            viewModelScope.launch {
                val snapshot = serializeAnnotation(removedAnnotation)
                val command = AnnotationCommand.RemoveAnnotation(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = _uiState.value.filePath,
                    pageIndex = removedAnnotation.pageIndex,
                    timestamp = System.currentTimeMillis(),
                    annotationId = removedAnnotation.id,
                    removedAnnotationPayload = snapshot
                )
                undoRedoManager.execute(command)
            }
        }
    }

    /**
     * Update an annotation and register it as an undoable command.
     * Stores both previous and new snapshots for undo/redo.
     */
    fun updateAnnotation(annotation: PdfAnnotation) {
        val previousAnnotation = _uiState.value.annotations.find { it.id == annotation.id }
        _uiState.update { s -> s.copy(annotations = s.annotations.map { if (it.id == annotation.id) annotation else it }) }

        if (previousAnnotation != null) {
            viewModelScope.launch {
                val prevSnapshot = serializeAnnotation(previousAnnotation)
                val newSnapshot = serializeAnnotation(annotation)
                val command = AnnotationCommand.UpdateAnnotation(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = _uiState.value.filePath,
                    pageIndex = annotation.pageIndex,
                    timestamp = System.currentTimeMillis(),
                    annotationId = annotation.id,
                    previousPayload = prevSnapshot,
                    newPayload = newSnapshot
                )
                undoRedoManager.execute(command)
            }
        }
    }

    /**
     * Set the currently selected annotation (e.g. to show the floating styling toolbar)
     */
    fun selectAnnotation(id: String?) {
        _uiState.update { it.copy(selectedAnnotationId = id) }
    }

    /**
     * Undo the last annotation action.
     */
    fun undo() {
        viewModelScope.launch {
            val command = undoRedoManager.undo() ?: return@launch
            applyUndoCommand(command)
            applyUndoForEditCommands(command)
        }
    }

    /**
     * Redo the last undone action.
     */
    fun redo() {
        viewModelScope.launch {
            val command = undoRedoManager.redo() ?: return@launch
            applyRedoCommand(command)
            applyRedoForEditCommands(command)
        }
    }

    /**
     * Apply the reverse of a command (undo).
     */
    private fun applyUndoCommand(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.AddPath -> {
                // Undo add = remove
                _uiState.update { s ->
                    s.copy(annotations = s.annotations.filter { it.id != command.annotationId })
                }
            }
            is AnnotationCommand.AddTextNote -> {
                // Undo add = remove
                _uiState.update { s ->
                    s.copy(annotations = s.annotations.filter { it.id != command.annotationId })
                }
            }
            is AnnotationCommand.RemoveAnnotation -> {
                // Undo remove = re-add the removed annotation
                val snapshot = CommandSerializer.deserializeSnapshot(command.removedAnnotationPayload)
                val annotation = snapshotToAnnotation(snapshot)
                if (annotation != null) {
                    _uiState.update { it.copy(annotations = it.annotations + annotation) }
                }
            }
            is AnnotationCommand.UpdateAnnotation -> {
                // Undo update = restore previous state
                val snapshot = CommandSerializer.deserializeSnapshot(command.previousPayload)
                val previousAnnotation = snapshotToAnnotation(snapshot)
                if (previousAnnotation != null) {
                    _uiState.update { s ->
                        s.copy(annotations = s.annotations.map {
                            if (it.id == command.annotationId) previousAnnotation else it
                        })
                    }
                }
            }
            is AnnotationCommand.TextCommand -> {
                // Undo means restoring "before"
                if (command.before == null) {
                    // Was newly created, so undo means removing it
                    _uiState.update { s ->
                        s.copy(annotations = s.annotations.filter { it.id != command.after.id })
                    }
                } else {
                    // It was an edit, restore previous state
                    val restoredNote = stateToTextNote(command.before, command.pageIndex)
                    _uiState.update { s ->
                        s.copy(annotations = s.annotations.map {
                            if (it.id == restoredNote.id) restoredNote else it
                        })
                    }
                }
            }
            // New command types handled by applyUndoForEditCommands()
            else -> {}
        }
    }

    /**
     * Re-apply a command (redo).
     */
    private fun applyRedoCommand(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.AddPath -> {
                // Redo add = re-add
                val annotation = PdfAnnotation.Path(
                    id = command.annotationId,
                    pageIndex = command.pageIndex,
                    points = command.points.map { Offset(it.x, it.y) },
                    color = Color(command.color.toULong()),
                    strokeWidth = command.strokeWidth,
                    isHighlighter = command.isHighlighter
                )
                _uiState.update { it.copy(annotations = it.annotations + annotation) }
            }
            is AnnotationCommand.AddTextNote -> {
                // Redo add = re-add
                val annotation = PdfAnnotation.TextNote(
                    id = command.annotationId,
                    pageIndex = command.pageIndex,
                    text = command.text,
                    position = Offset(command.positionX, command.positionY),
                    color = Color(command.color.toULong()),
                    fontSize = command.fontSize
                )
                _uiState.update { it.copy(annotations = it.annotations + annotation) }
            }
            is AnnotationCommand.RemoveAnnotation -> {
                // Redo remove = remove again
                _uiState.update { s ->
                    s.copy(annotations = s.annotations.filter { it.id != command.annotationId })
                }
            }
            is AnnotationCommand.UpdateAnnotation -> {
                // Redo update = apply new state
                val snapshot = CommandSerializer.deserializeSnapshot(command.newPayload)
                val newAnnotation = snapshotToAnnotation(snapshot)
                if (newAnnotation != null) {
                    _uiState.update { s ->
                        s.copy(annotations = s.annotations.map {
                            if (it.id == command.annotationId) newAnnotation else it
                        })
                    }
                }
            }
            is AnnotationCommand.TextCommand -> {
                // Redo means applying "after"
                val afterNote = stateToTextNote(command.after, command.pageIndex)
                _uiState.update { s ->
                    val existing = s.annotations.find { it.id == afterNote.id }
                    if (existing != null) {
                        s.copy(annotations = s.annotations.map { if (it.id == afterNote.id) afterNote else it })
                    } else {
                        s.copy(annotations = s.annotations + afterNote)
                    }
                }
            }
            // New command types handled by applyRedoForEditCommands()
            else -> {}
        }
    }

    // ─── Annotation ↔ Command Conversion Helpers ────────────────

    /**
     * Convert a PdfAnnotation to an AnnotationCommand for the undo/redo system.
     */
    private fun annotationToCommand(annotation: PdfAnnotation, pdfPath: String): AnnotationCommand? {
        return when (annotation) {
            is PdfAnnotation.Path -> {
                AnnotationCommand.AddPath(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = pdfPath,
                    pageIndex = annotation.pageIndex,
                    timestamp = System.currentTimeMillis(),
                    annotationId = annotation.id,
                    points = annotation.points.map { SerializableOffset(it.x, it.y) },
                    color = annotation.color.value.toLong(),
                    strokeWidth = annotation.strokeWidth,
                    isHighlighter = annotation.isHighlighter
                )
            }
            is PdfAnnotation.TextNote -> {
                AnnotationCommand.AddTextNote(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = pdfPath,
                    pageIndex = annotation.pageIndex,
                    timestamp = System.currentTimeMillis(),
                    annotationId = annotation.id,
                    text = annotation.text,
                    positionX = annotation.position.x,
                    positionY = annotation.position.y,
                    color = annotation.color.value.toLong(),
                    fontSize = annotation.fontSize
                )
            }
        }
    }

    /**
     * Convert an AnnotationCommand back to a PdfAnnotation for rendering.
     * Used during state restoration from DB.
     */
    private fun commandToAnnotation(command: AnnotationCommand): PdfAnnotation? {
        return when (command) {
            is AnnotationCommand.AddPath -> {
                PdfAnnotation.Path(
                    id = command.annotationId,
                    pageIndex = command.pageIndex,
                    points = command.points.map { Offset(it.x, it.y) },
                    color = Color(command.color.toULong()),
                    strokeWidth = command.strokeWidth,
                    isHighlighter = command.isHighlighter
                )
            }
            is AnnotationCommand.AddTextNote -> {
                PdfAnnotation.TextNote(
                    id = command.annotationId,
                    pageIndex = command.pageIndex,
                    text = command.text,
                    position = Offset(command.positionX, command.positionY),
                    color = Color(command.color.toULong()),
                    fontSize = command.fontSize
                )
            }
            is AnnotationCommand.RemoveAnnotation -> null // Remove commands don't create annotations
            is AnnotationCommand.UpdateAnnotation -> null // Update commands are handled during state replay
            is AnnotationCommand.TextCommand -> {
                PdfAnnotation.TextNote(
                    id = command.after.id,
                    pageIndex = command.pageIndex,
                    text = command.after.text,
                    position = Offset(command.after.positionX, command.after.positionY),
                    color = Color(command.after.color.toULong()),
                    fontSize = command.after.fontSize
                )
            }
            // New command types don't produce PdfAnnotation objects
            else -> null
        }
    }

    /**
     * Serialize a PdfAnnotation to a JSON snapshot string.
     */
    private fun serializeAnnotation(annotation: PdfAnnotation): String {
        return when (annotation) {
            is PdfAnnotation.Path -> {
                CommandSerializer.serializePathAnnotation(
                    annotationId = annotation.id,
                    pageIndex = annotation.pageIndex,
                    points = annotation.points.map { SerializableOffset(it.x, it.y) },
                    color = annotation.color.value.toLong(),
                    strokeWidth = annotation.strokeWidth,
                    isHighlighter = annotation.isHighlighter
                )
            }
            is PdfAnnotation.TextNote -> {
                CommandSerializer.serializeTextAnnotation(
                    annotationId = annotation.id,
                    pageIndex = annotation.pageIndex,
                    text = annotation.text,
                    positionX = annotation.position.x,
                    positionY = annotation.position.y,
                    color = annotation.color.value.toLong(),
                    fontSize = annotation.fontSize
                )
            }
        }
    }

    /**
     * Deserialize an annotation snapshot back to a PdfAnnotation.
     */
    private fun snapshotToAnnotation(snapshot: CommandSerializer.AnnotationSnapshot): PdfAnnotation? {
        return when (snapshot.type) {
            CommandSerializer.TYPE_ADD_PATH -> {
                val data = snapshot.pathData ?: return null
                PdfAnnotation.Path(
                    id = snapshot.annotationId,
                    pageIndex = snapshot.pageIndex,
                    points = data.points.map { Offset(it.x, it.y) },
                    color = Color(data.color.toULong()),
                    strokeWidth = data.strokeWidth,
                    isHighlighter = data.isHighlighter
                )
            }
            CommandSerializer.TYPE_ADD_TEXT -> {
                val data = snapshot.textData ?: return null
                PdfAnnotation.TextNote(
                    id = snapshot.annotationId,
                    pageIndex = snapshot.pageIndex,
                    text = data.text,
                    position = Offset(data.positionX, data.positionY),
                    color = Color(data.color.toULong()),
                    fontSize = data.fontSize
                )
            }
            else -> null
        }
    }

    // ─── Save Annotations with Undo/Redo Cleanup ────────────────

    fun saveAnnotationsToPdf(viewWidth: Int) {
        val path = uiState.value.filePath
        if (path.isEmpty() || uiState.value.annotations.isEmpty()) return
        val annotationsToSave = uiState.value.annotations.toList()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val file = File(path)
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                    for ((pageIndex, pageAnns) in annotationsToSave.groupBy { it.pageIndex }) {
                        val page = document.getPage(pageIndex)
                        val cropBox = page.cropBox; val pdfWidth = cropBox.width; val pdfHeight = cropBox.height
                        val scaleX = pdfWidth / viewWidth.toFloat()
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            for (ann in pageAnns) {
                                when (ann) {
                                    is PdfAnnotation.Path -> {
                                        if (ann.points.size < 2) continue
                                        val c = ann.color
                                        cs.setStrokingColor((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                                        cs.setLineWidth(ann.strokeWidth * scaleX)
                                        val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                                        gs.strokingAlphaConstant = if (ann.isHighlighter) 0.5f else 1.0f
                                        cs.setGraphicsStateParameters(gs)
                                        val s = ann.points.first(); cs.moveTo(s.x * scaleX, pdfHeight - (s.y * scaleX))
                                        for (i in 1 until ann.points.size) { val p = ann.points[i]; cs.lineTo(p.x * scaleX, pdfHeight - (p.y * scaleX)) }
                                        cs.stroke()
                                    }
                                    is PdfAnnotation.TextNote -> {
                                        cs.beginText(); val c = ann.color
                                        cs.setNonStrokingColor((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                                        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, ann.fontSize * scaleX)
                                        cs.newLineAtOffset(ann.position.x * scaleX, pdfHeight - (ann.position.y * scaleX) - (ann.fontSize * scaleX))
                                        cs.showText(ann.text); cs.endText()
                                    }
                                }
                            }
                        }
                    }
                    document.save(file)
                }
                withContext(pdfDispatcher) {
                    pdfRenderer?.close(); clearBitmapCache(); _pageStates.value = emptyMap()
                    pdfRenderer = PdfPageRenderer(getApplication(), path, uiState.value.password)
                }
                _uiState.update { it.copy(annotations = emptyList(), reloadTrigger = it.reloadTrigger + 1) }

                // Clear all undo/redo history — annotations are now baked into the PDF
                undoRedoManager.clearAll(path)
            } catch (e: Exception) { Log.e(TAG, "saveAnnotations failed", e)
            } finally { _uiState.update { it.copy(isLoading = false, isEditMode = false) } }
        }
    }

    // ─── UI State Mutations ─────────────────────────────────────

    fun setEditMode(isEditMode: Boolean) { _uiState.update { it.copy(isEditMode = isEditMode) } }
    fun setAnnotationTool(tool: AnnotationTool) { _uiState.update { it.copy(currentTool = tool) } }
    fun setAnnotationColor(color: Color) { _uiState.update { it.copy(currentColor = color) } }
    fun setAnnotationStrokeWidth(width: Float) { _uiState.update { it.copy(currentStrokeWidth = width) } }

    fun submitPassword(password: String) {
        val path = uiState.value.filePath
        if (path.isNotEmpty()) { _uiState.update { it.copy(password = password, isLoading = true) }; initialize(path, password) }
    }
    fun onPasswordChange(p: String) { _uiState.update { it.copy(password = p) } }

    fun updateCurrentPage(page: Int) {
        if (page != _uiState.value.currentPage) {
            _uiState.update { s -> s.copy(currentPage = page, isBookmarked = s.bookmarks.any { it.pageIndex == page }) }
        }
    }

    private fun observeBookmarks(path: String) {
        viewModelScope.launch {
            pdfRepository.getBookmarksForPdf(path).collect { bookmarks ->
                _uiState.update { s -> s.copy(bookmarks = bookmarks, isBookmarked = bookmarks.any { it.pageIndex == s.currentPage }) }
            }
        }
    }

    fun toggleBookmark() {
        val path = uiState.value.filePath; val page = uiState.value.currentPage; if (path.isEmpty()) return
        viewModelScope.launch {
            if (uiState.value.isBookmarked) pdfRepository.removeBookmark(path, page) else pdfRepository.addBookmark(path, page)
        }
    }

    fun toggleNightMode() {
        val newMode = if (_uiState.value.isNightMode) BackgroundMode.ORIGINAL else BackgroundMode.INVERT
        updateViewSettings(_uiState.value.viewSettings.copy(backgroundMode = newMode))
    }

    /**
     * Update the centralized view settings. Persists to DataStore.
     * If the background mode changes to/from INVERT, triggers a full re-render.
     */
    fun updateViewSettings(newSettings: ViewSettings) {
        val oldSettings = _uiState.value.viewSettings
        val needsReRender = oldSettings.backgroundMode != newSettings.backgroundMode &&
            (oldSettings.backgroundMode == BackgroundMode.INVERT || newSettings.backgroundMode == BackgroundMode.INVERT)

        if (needsReRender) {
            _uiState.update { it.copy(viewSettings = newSettings, isLoading = true) }
            viewModelScope.launch {
                clearBitmapCache(); _pageStates.value = emptyMap()
                _uiState.update { it.copy(isLoading = false, reloadTrigger = it.reloadTrigger + 1) }
                preferenceManager.saveViewSettings(newSettings)
            }
        } else {
            _uiState.update { it.copy(viewSettings = newSettings) }
            viewModelScope.launch {
                preferenceManager.saveViewSettings(newSettings)
            }
        }
    }

    private fun clearBitmapCache() { bitmapCache.evictAll() }

    // ─── Text Block Extraction & Editing ────────────────────────

    /**
     * Extract text blocks from the PDF using PdfBox.
     * Called automatically when EDIT_TEXT tool is activated.
     */
    fun extractTextBlocks() {
        val path = _uiState.value.filePath
        if (path.isEmpty()) return
        if (_uiState.value.textBlocks.isNotEmpty()) return // Already extracted

        _uiState.update { it.copy(isTextBlocksLoading = true) }
        viewModelScope.launch {
            val blocks = textBlockExtractor.extractTextBlocks(path)
            _uiState.update { it.copy(textBlocks = blocks, isTextBlocksLoading = false) }
            Log.d(TAG, "Extracted text blocks: ${blocks.values.sumOf { it.size }} blocks across ${blocks.size} pages")
        }
    }

    /**
     * Select a text block for editing.
     */
    fun selectTextBlock(id: String?) {
        _uiState.update { it.copy(selectedTextBlockId = id) }
    }

    /**
     * Apply an edit to a detected text block.
     * Creates an undo-able command.
     */
    fun editTextBlock(blockId: String, newText: String, newFontSize: Float, newColor: Color) {
        val allBlocks = _uiState.value.textBlocks.values.flatten()
        val originalBlock = allBlocks.find { it.id == blockId } ?: return

        // Check if there's already an edit for this block
        val existingEdit = _uiState.value.editedTextBlocks.find { it.originalBlock.id == blockId }

        val newEditedBlock = EditedTextBlock(
            originalBlock = originalBlock,
            newText = newText,
            newFontSize = newFontSize,
            newColor = newColor
        )

        _uiState.update { state ->
            val updatedEdits = if (existingEdit != null) {
                state.editedTextBlocks.map { if (it.originalBlock.id == blockId) newEditedBlock else it }
            } else {
                state.editedTextBlocks + newEditedBlock
            }
            state.copy(editedTextBlocks = updatedEdits, selectedTextBlockId = null)
        }

        // Register undo command
        viewModelScope.launch {
            val beforeState = existingEdit?.let {
                AnnotationCommand.EditTextState(
                    blockId = blockId,
                    originalText = it.originalBlock.text,
                    newText = it.newText,
                    originalFontSize = it.originalBlock.fontSize,
                    newFontSize = it.newFontSize,
                    newColor = it.newColor.value.toLong(),
                    x = it.originalBlock.x,
                    y = it.originalBlock.y,
                    width = it.originalBlock.width,
                    height = it.originalBlock.height
                )
            }

            val afterState = AnnotationCommand.EditTextState(
                blockId = blockId,
                originalText = originalBlock.text,
                newText = newText,
                originalFontSize = originalBlock.fontSize,
                newFontSize = newFontSize,
                newColor = newColor.value.toLong(),
                x = originalBlock.x,
                y = originalBlock.y,
                width = originalBlock.width,
                height = originalBlock.height
            )

            val command = AnnotationCommand.EditTextCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = originalBlock.pageIndex,
                timestamp = System.currentTimeMillis(),
                before = beforeState,
                after = afterState
            )
            undoRedoManager.execute(command)
        }
    }

    // ─── Image Manipulation ─────────────────────────────────────

    /** Stores the image position before a drag begins (for move undo) */
    private var imageMoveStartPosition: Offset? = null
    /** Stores the image bounds before a resize begins */
    private var imageResizeStartState: ImageElement? = null

    /**
     * Add an image from the given URI onto the current page.
     */
    fun addImage(uri: Uri, pageIndex: Int, viewWidth: Int) {
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get image dimensions
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                }

                val imgWidth = options.outWidth.toFloat()
                val imgHeight = options.outHeight.toFloat()
                if (imgWidth <= 0 || imgHeight <= 0) return@launch

                // Scale image to fit reasonably on the page (max 50% of view width)
                val maxWidth = viewWidth * 0.5f
                val scale = if (imgWidth > maxWidth) maxWidth / imgWidth else 1f
                val displayWidth = imgWidth * scale
                val displayHeight = imgHeight * scale

                // Place at center of visible area
                val posX = (viewWidth - displayWidth) / 2f
                val posY = 100f // Near top of page

                val element = ImageElement(
                    pageIndex = pageIndex,
                    uri = uri.toString(),
                    position = Offset(posX, posY),
                    width = displayWidth,
                    height = displayHeight
                )

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        imageElements = it.imageElements + element,
                        selectedImageId = element.id
                    )}
                }

                // Register undo command
                val command = AnnotationCommand.AddImageCommand(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = _uiState.value.filePath,
                    pageIndex = pageIndex,
                    timestamp = System.currentTimeMillis(),
                    imageState = AnnotationCommand.ImageState(
                        elementId = element.id,
                        uri = uri.toString(),
                        positionX = posX,
                        positionY = posY,
                        width = displayWidth,
                        height = displayHeight,
                        scale = 1f,
                        rotation = 0f
                    )
                )
                undoRedoManager.execute(command)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add image", e)
            }
        }
    }

    /**
     * Select an image element.
     */
    fun selectImage(id: String?) {
        _uiState.update { it.copy(selectedImageId = id) }
    }

    /**
     * Move an image by a delta offset during drag.
     */
    fun moveImage(id: String, delta: Offset) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return

        // Capture start position on first move
        if (imageMoveStartPosition == null) {
            imageMoveStartPosition = element.position
        }

        _uiState.update { state ->
            state.copy(imageElements = state.imageElements.map {
                if (it.id == id) it.copy(position = it.position + delta) else it
            })
        }
    }

    /**
     * Finalize a move operation — create undo command.
     */
    fun onMoveEnd(id: String) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return
        val startPos = imageMoveStartPosition ?: return
        imageMoveStartPosition = null

        if (startPos == element.position) return // No actual move

        viewModelScope.launch {
            val command = AnnotationCommand.MoveImageCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = element.pageIndex,
                timestamp = System.currentTimeMillis(),
                elementId = id,
                beforeX = startPos.x,
                beforeY = startPos.y,
                afterX = element.position.x,
                afterY = element.position.y
            )
            undoRedoManager.execute(command)
        }
    }

    /**
     * Resize an image via a resize handle drag delta.
     */
    fun resizeImage(id: String, handle: ResizeHandle, delta: Offset) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return

        // Capture start state on first resize delta
        if (imageResizeStartState == null) {
            imageResizeStartState = element
        }

        val minSize = 30f
        var newX = element.position.x
        var newY = element.position.y
        var newW = element.width
        var newH = element.height

        when (handle) {
            ResizeHandle.BOTTOM_RIGHT -> {
                newW = (newW + delta.x).coerceAtLeast(minSize)
                newH = (newH + delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.BOTTOM_LEFT -> {
                newX += delta.x
                newW = (newW - delta.x).coerceAtLeast(minSize)
                newH = (newH + delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.TOP_RIGHT -> {
                newY += delta.y
                newW = (newW + delta.x).coerceAtLeast(minSize)
                newH = (newH - delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.TOP_LEFT -> {
                newX += delta.x
                newY += delta.y
                newW = (newW - delta.x).coerceAtLeast(minSize)
                newH = (newH - delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.TOP_CENTER -> {
                newY += delta.y
                newH = (newH - delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.BOTTOM_CENTER -> {
                newH = (newH + delta.y).coerceAtLeast(minSize)
            }
            ResizeHandle.LEFT_CENTER -> {
                newX += delta.x
                newW = (newW - delta.x).coerceAtLeast(minSize)
            }
            ResizeHandle.RIGHT_CENTER -> {
                newW = (newW + delta.x).coerceAtLeast(minSize)
            }
        }

        _uiState.update { state ->
            state.copy(imageElements = state.imageElements.map {
                if (it.id == id) it.copy(
                    position = Offset(newX, newY),
                    width = newW,
                    height = newH
                ) else it
            })
        }
    }

    /**
     * Finalize a resize operation — create undo command.
     */
    fun onResizeEnd(id: String) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return
        val startState = imageResizeStartState ?: return
        imageResizeStartState = null

        viewModelScope.launch {
            val command = AnnotationCommand.ResizeImageCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = element.pageIndex,
                timestamp = System.currentTimeMillis(),
                elementId = id,
                beforeWidth = startState.width,
                beforeHeight = startState.height,
                afterWidth = element.width,
                afterHeight = element.height,
                beforeX = startState.position.x,
                beforeY = startState.position.y,
                afterX = element.position.x,
                afterY = element.position.y
            )
            undoRedoManager.execute(command)
        }
    }

    /**
     * Rotate an image by the given degrees (typically ±90).
     */
    fun rotateImage(id: String, degrees: Float) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return
        val oldRotation = element.rotation
        val newRotation = (oldRotation + degrees) % 360f

        _uiState.update { state ->
            state.copy(imageElements = state.imageElements.map {
                if (it.id == id) it.copy(rotation = newRotation) else it
            })
        }

        viewModelScope.launch {
            val command = AnnotationCommand.RotateImageCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = element.pageIndex,
                timestamp = System.currentTimeMillis(),
                elementId = id,
                beforeRotation = oldRotation,
                afterRotation = newRotation
            )
            undoRedoManager.execute(command)
        }
    }

    /**
     * Delete an image element. Stores full state for undo.
     */
    fun deleteImage(id: String) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return

        _uiState.update { state ->
            state.copy(
                imageElements = state.imageElements.filter { it.id != id },
                selectedImageId = if (state.selectedImageId == id) null else state.selectedImageId
            )
        }

        viewModelScope.launch {
            val command = AnnotationCommand.DeleteImageCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = _uiState.value.filePath,
                pageIndex = element.pageIndex,
                timestamp = System.currentTimeMillis(),
                deletedImageState = AnnotationCommand.ImageState(
                    elementId = element.id,
                    uri = element.uri,
                    positionX = element.position.x,
                    positionY = element.position.y,
                    width = element.width,
                    height = element.height,
                    scale = element.scale,
                    rotation = element.rotation
                )
            )
            undoRedoManager.execute(command)
        }
    }

    // ─── Enhanced Tool Change ───────────────────────────────────

    /**
     * Override setAnnotationTool to auto-trigger text extraction when
     * EDIT_TEXT mode is activated.
     */
    fun setAnnotationToolWithAutoExtract(tool: AnnotationTool) {
        _uiState.update { it.copy(currentTool = tool) }
        if (tool == AnnotationTool.EDIT_TEXT) {
            extractTextBlocks()
        }
    }

    /**
     * Set overlay interaction state (drag/resize in progress).
     * Used to disable parent scroll during image/text manipulation.
     */
    fun setOverlayInteracting(interacting: Boolean) {
        _uiState.update { it.copy(isOverlayInteracting = interacting) }
    }

    // ─── Export Edited PDF ──────────────────────────────────────

    /**
     * Export the edited PDF with all text edits and image overlays.
     * Saves as `<name>_edited.pdf`. Does NOT modify original.
     */
    fun exportEditedPdf(viewWidth: Int) {
        val state = _uiState.value
        if (!state.hasEditableOverlays) return

        _uiState.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            val result = pdfExportManager.exportEditedPdf(
                context = getApplication(),
                originalPath = state.filePath,
                editedTextBlocks = state.editedTextBlocks,
                imageElements = state.imageElements,
                viewWidth = viewWidth
            )

            _uiState.update { it.copy(
                isExporting = false,
                exportResult = result
            )}

            if (result != null) {
                Log.d(TAG, "Export successful: $result")
            } else {
                Log.e(TAG, "Export failed")
            }
        }
    }

    /**
     * Clear the export result message.
     */
    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    // ─── Enhanced Undo/Redo for New Commands ────────────────────

    /**
     * Extended undo handler that also handles text edit and image commands.
     */
    private fun applyUndoForEditCommands(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.EditTextCommand -> {
                if (command.before == null) {
                    // Was a new edit — undo means remove it
                    _uiState.update { state ->
                        state.copy(editedTextBlocks = state.editedTextBlocks.filter {
                            it.originalBlock.id != command.after.blockId
                        })
                    }
                } else {
                    // Restore previous edit state
                    _uiState.update { state ->
                        state.copy(editedTextBlocks = state.editedTextBlocks.map {
                            if (it.originalBlock.id == command.before.blockId) {
                                it.copy(
                                    newText = command.before.newText,
                                    newFontSize = command.before.newFontSize,
                                    newColor = Color(command.before.newColor.toULong())
                                )
                            } else it
                        })
                    }
                }
            }
            is AnnotationCommand.AddImageCommand -> {
                // Undo add = remove
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.filter {
                        it.id != command.imageState.elementId
                    })
                }
            }
            is AnnotationCommand.MoveImageCommand -> {
                // Undo move = restore previous position
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(position = Offset(command.beforeX, command.beforeY))
                        } else it
                    })
                }
            }
            is AnnotationCommand.ResizeImageCommand -> {
                // Undo resize = restore previous dimensions
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(
                                position = Offset(command.beforeX, command.beforeY),
                                width = command.beforeWidth,
                                height = command.beforeHeight
                            )
                        } else it
                    })
                }
            }
            is AnnotationCommand.RotateImageCommand -> {
                // Undo rotate = restore previous rotation
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(rotation = command.beforeRotation)
                        } else it
                    })
                }
            }
            is AnnotationCommand.DeleteImageCommand -> {
                // Undo delete = re-add the image
                val imgState = command.deletedImageState
                val element = ImageElement(
                    id = imgState.elementId,
                    pageIndex = command.pageIndex,
                    uri = imgState.uri,
                    position = Offset(imgState.positionX, imgState.positionY),
                    width = imgState.width,
                    height = imgState.height,
                    scale = imgState.scale,
                    rotation = imgState.rotation
                )
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements + element)
                }
            }
            else -> {} // handled by existing applyUndoCommand
        }
    }

    /**
     * Extended redo handler for new command types.
     */
    private fun applyRedoForEditCommands(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.EditTextCommand -> {
                val allBlocks = _uiState.value.textBlocks.values.flatten()
                val original = allBlocks.find { it.id == command.after.blockId } ?: return
                val newEdit = EditedTextBlock(
                    originalBlock = original,
                    newText = command.after.newText,
                    newFontSize = command.after.newFontSize,
                    newColor = Color(command.after.newColor.toULong())
                )
                _uiState.update { state ->
                    val existing = state.editedTextBlocks.find { it.originalBlock.id == command.after.blockId }
                    if (existing != null) {
                        state.copy(editedTextBlocks = state.editedTextBlocks.map {
                            if (it.originalBlock.id == command.after.blockId) newEdit else it
                        })
                    } else {
                        state.copy(editedTextBlocks = state.editedTextBlocks + newEdit)
                    }
                }
            }
            is AnnotationCommand.AddImageCommand -> {
                val imgState = command.imageState
                val element = ImageElement(
                    id = imgState.elementId,
                    pageIndex = command.pageIndex,
                    uri = imgState.uri,
                    position = Offset(imgState.positionX, imgState.positionY),
                    width = imgState.width,
                    height = imgState.height,
                    scale = imgState.scale,
                    rotation = imgState.rotation
                )
                _uiState.update { it.copy(imageElements = it.imageElements + element) }
            }
            is AnnotationCommand.MoveImageCommand -> {
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(position = Offset(command.afterX, command.afterY))
                        } else it
                    })
                }
            }
            is AnnotationCommand.ResizeImageCommand -> {
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(
                                position = Offset(command.afterX, command.afterY),
                                width = command.afterWidth,
                                height = command.afterHeight
                            )
                        } else it
                    })
                }
            }
            is AnnotationCommand.RotateImageCommand -> {
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.map {
                        if (it.id == command.elementId) {
                            it.copy(rotation = command.afterRotation)
                        } else it
                    })
                }
            }
            is AnnotationCommand.DeleteImageCommand -> {
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements.filter {
                        it.id != command.deletedImageState.elementId
                    })
                }
            }
            else -> {} // handled by existing applyRedoCommand
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        val path = _uiState.value.filePath; val page = _uiState.value.currentPage
        if (path.isNotEmpty() && page >= 0) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try { pdfRepository.updateLastOpenedPage(path, page) } catch (e: Exception) { e.printStackTrace() }
            }
        }
        activeRenderJobs.values.forEach { it.cancel() }; activeRenderJobs.clear()
        searchJob?.cancel(); pdfRenderer?.close(); clearBitmapCache()
    }
}

