package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.domain.model.PdfAnnotation
import com.pdf.pdfreader.ui.components.AnnotationTool
import com.pdf.pdfreader.utiles.PdfPageRenderer
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
    val isNightMode: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val totalMatchCount: Int = 0
)

sealed class ScrollEvent {
    data class ScrollToPage(val pageIndex: Int) : ScrollEvent()
}

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    application: Application,
    private val pdfRepository: com.pdf.pdfreader.domain.repository.PdfRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PdfReaderVM"
        private const val MAX_CACHE_SIZE_KB = 15 * 1024
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
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    // Per-page Job tracking — each page gets its own coroutine
    private val activeRenderJobs = ConcurrentHashMap<Int, Job>()
    // Per-page failure count for auto-recovery
    private val failureCounts = ConcurrentHashMap<Int, Int>()

    // ─── Initialize ─────────────────────────────────────────────

    fun initialize(path: String, password: String? = null) {
        viewModelScope.launch {
            val name = File(path).name
            var initialPage = 0
            try {
                val existing = pdfRepository.getPdfFiles().first().find { it.path == path }
                if (existing != null) initialPage = existing.lastOpenedPage
            } catch (e: Exception) { e.printStackTrace() }

            _uiState.update {
                it.copy(filePath = path, fileName = name, isLoading = true, errorMessage = null, currentPage = initialPage)
            }
            observeBookmarks(path)

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

    // ─── Core Rendering (Per-Page Job Architecture) ─────────────

    /**
     * Request a page render. Each page gets its own coroutine.
     * - Returns immediately from cache if available
     * - Skips if this page already has an active render job
     * - Launches a new Job on viewModelScope for rendering
     */
    fun requestPageRender(pageIndex: Int, width: Int) {
        if (width <= 0) return

        val cacheKey = "${pageIndex}_${width}"

        // Fast path: return from cache
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            updatePageState(pageIndex, PageRenderState.Success(cached, width))
            return
        }

        // Skip if already actively rendering this page
        val existingJob = activeRenderJobs[pageIndex]
        if (existingJob != null && existingJob.isActive) return

        // Launch a NEW coroutine for this page
        val job = viewModelScope.launch {
            renderPageInternal(pageIndex, width)
        }
        activeRenderJobs[pageIndex] = job
    }

    /**
     * Retry: cancels old job, clears error state, starts fresh render.
     */
    fun retryPageRender(pageIndex: Int, width: Int) {
        Log.d(TAG, "retryPageRender: page=$pageIndex")
        // Cancel any existing job for this page
        activeRenderJobs.remove(pageIndex)?.cancel()
        // Force state to Loading (clears Error)
        updatePageState(pageIndex, PageRenderState.Loading)
        // Launch a completely new job
        val job = viewModelScope.launch {
            renderPageInternal(pageIndex, width)
        }
        activeRenderJobs[pageIndex] = job
    }

    /**
     * The actual rendering logic. Runs as a coroutine per page.
     * Handles: renderer health check, rendering, caching, error recovery.
     */
    private suspend fun renderPageInternal(pageIndex: Int, width: Int, height: Int = 0) {
        val cacheKey = if (height > 0) "${pageIndex}_${width}x${height}" else "${pageIndex}_${width}"

        // Double-check cache (another job might have rendered it)
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            updatePageState(pageIndex, PageRenderState.Success(cached, width))
            return
        }

        // Only show Loading if page isn't already rendered (prevents blink during search navigation)
        val currentState = _pageStates.value[pageIndex]
        if (currentState !is PageRenderState.Success) {
            updatePageState(pageIndex, PageRenderState.Loading)
        }

        val bitmap: Bitmap? = try {
            withContext(pdfDispatcher) {
                if (!isActive) return@withContext null

                // Health check: ensure renderer is ready
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
            // Handle failure with auto-recovery
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

                // One final retry after reinitialize
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

    /** Must be called on pdfDispatcher */
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
        activeRenderJobs.forEach { (pageIndex, job) ->
            if (pageIndex !in visibleRange) {
                job.cancel()
                activeRenderJobs.remove(pageIndex)
            }
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

    // ─── Annotations ────────────────────────────────────────────

    fun addAnnotation(annotation: PdfAnnotation) { _uiState.update { it.copy(annotations = it.annotations + annotation) } }
    fun removeAnnotation(id: String) { _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != id }) } }
    fun updateAnnotation(annotation: PdfAnnotation) { _uiState.update { s -> s.copy(annotations = s.annotations.map { if (it.id == annotation.id) annotation else it }) } }

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
        _uiState.update { it.copy(isNightMode = !it.isNightMode, isLoading = true) }
        viewModelScope.launch {
            clearBitmapCache(); _pageStates.value = emptyMap()
            _uiState.update { it.copy(isLoading = false, reloadTrigger = it.reloadTrigger + 1) }
        }
    }

    private fun clearBitmapCache() { bitmapCache.evictAll() }

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
