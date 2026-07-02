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
import com.pdf.pdfreader.domain.model.InteractionMode
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
data class TextSelectionState(
    val pageIndex: Int,
    val selectedWords: List<com.pdf.pdfreader.domain.model.TextWord>,
    val bounds: androidx.compose.ui.geometry.Rect?,
    // The two anchor words the start/end handles map to. selectedWords is the inclusive range
    // between them in reading order. Null only for legacy/empty selections.
    val startWord: com.pdf.pdfreader.domain.model.TextWord? = null,
    val endWord: com.pdf.pdfreader.domain.model.TextWord? = null
)

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
    val isBookmarked: Boolean = false,
    val bookmarks: List<com.pdf.pdfreader.data.local.BookmarkEntity> = emptyList(),
    // ─── View Settings ─────────────────────────────────────
    val viewSettings: ViewSettings = ViewSettings(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val totalMatchCount: Int = 0,
    val searchCaseSensitive: Boolean = false,
    val searchWholeWord: Boolean = false
) {
    /** Convenience: true when background mode is INVERT */
    val isNightMode: Boolean get() = viewSettings.backgroundMode == BackgroundMode.INVERT
}

/** Identifies the type of element currently under user interaction */
enum class SelectedElementType {
    NONE, TEXT, IMAGE, SIGNATURE, GROUP
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
    private val pdfExportManager: PdfExportManager,
    private val signatureManager: com.pdf.pdfreader.domain.repository.SignatureManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PdfReaderVM"
        private val MAX_CACHE_SIZE_KB = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt().coerceAtLeast(80 * 1024)
        private const val MAX_RETRY_BEFORE_REINIT = 2
        private const val ZOOM_RERENDER_THRESHOLD = 1.25f
        // Must match MAX_ZOOM in PdfReaderScreen so the re-rendered bitmap matches
        // the maximum on-screen zoom and stays crisp (and bounds memory use).
        private const val MAX_ZOOM_RENDER_SCALE = 5f
        // Upper bound on a single high-res bitmap so a deep zoom can't OOM. A full
        // page at 5x on a 1080px screen would be ~40MP (~165MB ARGB_8888), so we cap
        // total pixels to ~1/8 of the heap and downscale the render to fit — the
        // graphicsLayer magnifies any remainder. Bounded to a sane 6–24MP window.
        private val MAX_RENDER_PIXELS = (Runtime.getRuntime().maxMemory() / 32L)
            .coerceIn(6_000_000L, 24_000_000L)
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
    private val groupResizeStartStates = mutableMapOf<String, ImageElement>()

    init {
        // Init logic for reader (undo/redo logic moved to EditorViewModel)
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
        // Bound the render scale so a deep zoom can't allocate an enormous bitmap.
        val clampedScale = zoomScale.coerceAtMost(MAX_ZOOM_RENDER_SCALE)
        viewModelScope.launch {
            val dims = withContext(pdfDispatcher) { pdfRenderer?.getPageDimensions(pageIndex) } ?: return@launch
            val ratio = dims.second.toFloat() / dims.first.toFloat()
            var targetWidth = (baseWidth * clampedScale).toInt()
            var targetHeight = (targetWidth * ratio).toInt()
            // Keep the bitmap within the pixel budget; downscale (preserving aspect
            // ratio) rather than allocating a page too large for the heap.
            val pixels = targetWidth.toLong() * targetHeight.toLong()
            if (pixels > MAX_RENDER_PIXELS) {
                val shrink = kotlin.math.sqrt(MAX_RENDER_PIXELS.toDouble() / pixels.toDouble()).toFloat()
                targetWidth = (targetWidth * shrink).toInt().coerceAtLeast(baseWidth)
                targetHeight = (targetWidth * ratio).toInt()
            }
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
                    // Only recycle if this bitmap is no longer referenced by the cache.
                    // Cache keys vary (base "page_W" vs high-res "page_WxH"), so match by
                    // reference rather than reconstructing keys, which previously missed
                    // high-res bitmaps and could recycle one still held in the cache.
                    val stillCached = bitmapCache.snapshot().values.any { it === state.bitmap }
                    if (!stillCached && !state.bitmap.isRecycled) {
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
        rerunSearch(debounce = true)
    }

    /** Toggle case-sensitive matching and re-run the current query. */
    fun toggleSearchCaseSensitive() {
        _uiState.update { it.copy(searchCaseSensitive = !it.searchCaseSensitive) }
        rerunSearch(debounce = false)
    }

    /** Toggle whole-word matching and re-run the current query. */
    fun toggleSearchWholeWord() {
        _uiState.update { it.copy(searchWholeWord = !it.searchWholeWord) }
        rerunSearch(debounce = false)
    }

    private fun rerunSearch(debounce: Boolean) {
        val query = _uiState.value.searchQuery
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), currentMatchIndex = -1, totalMatchCount = 0) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            searchInPdf(query)
        }
    }

    /**
     * Finds every match range of [query] inside [text], honoring case sensitivity and
     * whole-word boundaries. Whole-word means the match is not flanked by letters/digits.
     */
    private fun matchRangesIn(
        text: String, query: String, caseSensitive: Boolean, wholeWord: Boolean
    ): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val haystack = if (caseSensitive) text else text.lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        val ranges = mutableListOf<IntRange>()
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            val end = idx + needle.length
            val boundaryOk = !wholeWord || (
                (idx == 0 || !haystack[idx - 1].isLetterOrDigit()) &&
                (end >= haystack.length || !haystack[end].isLetterOrDigit())
            )
            if (boundaryOk) ranges.add(idx until end)
            idx = haystack.indexOf(needle, idx + 1)
        }
        return ranges
    }

    private suspend fun searchInPdf(query: String) {
        val path = _uiState.value.filePath
        if (path.isEmpty()) return
        val caseSensitive = _uiState.value.searchCaseSensitive
        val wholeWord = _uiState.value.searchWholeWord
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
                                for (range in matchRangesIn(text, query, caseSensitive, wholeWord)) {
                                    val startIndex = range.first
                                    val endIndex = range.last + 1
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
                                            text.substring(startIndex, endIndex.coerceAtMost(text.length))))
                                    }
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
        // Single indexed pass (was O(n²): filter + indexOf per match).
        return state.searchResults.mapIndexedNotNull { globalIndex, match ->
            if (match.pageIndex != pageIndex) return@mapIndexedNotNull null
            Pair(Rect(match.rect.left * viewWidth, match.rect.top * viewHeight,
                match.rect.right * viewWidth, match.rect.bottom * viewHeight), globalIndex == state.currentMatchIndex)
        }
    }

    // ─── UI State Mutations ─────────────────────────────────────


    fun submitPassword(password: String) {
        val path = uiState.value.filePath
        if (path.isNotEmpty()) { _uiState.update { it.copy(password = password, isLoading = true) }; initialize(path, password) }
    }
    fun onPasswordChange(p: String) { _uiState.update { it.copy(password = p) } }

    private var positionSaveJob: Job? = null

    fun updateCurrentPage(page: Int) {
        if (page != _uiState.value.currentPage) {
            _uiState.update { s -> s.copy(currentPage = page, isBookmarked = s.bookmarks.any { it.pageIndex == page }) }
            persistReadingPosition(page)
        }
    }

    /**
     * Persists the current reading position so the document re-opens where the user
     * left off. Debounced because [updateCurrentPage] fires on every scroll tick.
     * The read side already exists in [initialize] (lastOpenedPage); this is the
     * previously-missing write side.
     */
    private fun persistReadingPosition(page: Int) {
        val path = _uiState.value.filePath
        if (path.isEmpty()) return
        positionSaveJob?.cancel()
        positionSaveJob = viewModelScope.launch {
            delay(500)
            try {
                pdfRepository.updateLastOpenedPage(path, page)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist reading position", e)
            }
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

    /** Remove the bookmark on a specific page (used by the bookmarks list). */
    fun removeBookmarkAt(pageIndex: Int) {
        val path = uiState.value.filePath; if (path.isEmpty()) return
        viewModelScope.launch { pdfRepository.removeBookmark(path, pageIndex) }
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
}
