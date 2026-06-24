package com.pdf.pdfreader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.R
import com.pdf.pdfreader.ui.components.AnnotationTopBar
import com.pdf.pdfreader.ui.components.PdfAnnotationOverlay
import com.pdf.pdfreader.ui.viewmodel.PageRenderState
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import com.pdf.pdfreader.ui.viewmodel.ScrollEvent
import com.pdf.pdfreader.domain.model.BackgroundMode
import com.pdf.pdfreader.domain.model.ReadingMode
import com.pdf.pdfreader.ui.components.ViewOptionsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/** Maximum zoom factor. Kept in sync with the ViewModel's high-res render cap
 *  so the re-rendered bitmap always matches the on-screen zoom (stays crisp). */
private const val MAX_ZOOM = 3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel,
    path: String,
    initialPageIndex: Int = -1,
    searchQuery: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToManagePages: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val pageStates by viewModel.pageStates.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(path) {
        viewModel.initialize(path)
        editorViewModel.initialize(path)
    }

    // Handle initial page / external search query
    LaunchedEffect(uiState.totalPages) {
        if (uiState.totalPages > 0) {
            val targetPage = when {
                initialPageIndex in 0 until uiState.totalPages -> initialPageIndex
                uiState.currentPage in 0 until uiState.totalPages -> uiState.currentPage
                else -> 0
            }
            if (targetPage > 0) {
                scrollState.scrollToItem(targetPage)
            }
            // If an external search query was passed, activate search
            if (!searchQuery.isNullOrEmpty() && !uiState.isSearchActive) {
                viewModel.toggleSearch()
                viewModel.updateSearchQuery(searchQuery)
            }
        }
    }

    // Listen for search scroll events
    LaunchedEffect(Unit) {
        viewModel.scrollEvents.collect { event ->
            when (event) {
                is ScrollEvent.ScrollToPage -> {
                    scrollState.animateScrollToItem(event.pageIndex)
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // True while 2+ fingers are down — used to suspend list scrolling during a pinch.
    var multiTouch by remember { mutableStateOf(false) }

    // ─── Crisp-zoom: re-render the focused page at the zoomed resolution ───
    // graphicsLayer only magnifies the existing bitmap (blurry). When the user
    // zooms in we render a fresh, higher-resolution bitmap for the current page
    // so text/graphics stay sharp — similar to Google Drive's PDF viewer.
    LaunchedEffect(scale, uiState.currentPage) {
        if (scale > 1f) {
            // Short debounce: while the pinch is ongoing, `scale` keeps changing and
            // restarts this effect, so the high-res render only fires once the gesture
            // settles — then the page sharpens to match the zoom.
            delay(120)
            viewModel.requestHighResRender(uiState.currentPage, screenWidthPx, scale)
        } else {
            // Zoomed back out — restore the cached base-resolution render
            viewModel.requestPageRender(uiState.currentPage, screenWidthPx)
        }
    }

    val isScrolling by remember {
        derivedStateOf { scrollState.isScrollInProgress }
    }

    var isSliderVisible by remember { mutableStateOf(false) }
    var sliderInteractionTime by remember { mutableLongStateOf(0L) }
    var showViewOptions by remember { mutableStateOf(false) }

    val viewSettings = uiState.viewSettings

    // Auto-hide slider logic
    LaunchedEffect(isScrolling, sliderInteractionTime) {
        isSliderVisible = true
        if (!isScrolling) {
            delay(2500)
            isSliderVisible = false
        }
    }

    // Update current page and cancel far-off renders
    LaunchedEffect(scrollState.firstVisibleItemIndex) {
        val firstVisible = scrollState.firstVisibleItemIndex
        val lastVisible = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
        val safeRange = (firstVisible - 2).coerceAtLeast(0)..(lastVisible + 2).coerceAtMost(uiState.totalPages - 1)
        viewModel.cancelRenderingOutsideRange(safeRange)
        viewModel.updateCurrentPage(firstVisible)
        editorViewModel.setCurrentPage(firstVisible)
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            editorViewModel.addImage(it, uiState.currentPage, screenWidthPx)
        }
    }

    // Launch image picker when INSERT_IMAGE tool is selected
    LaunchedEffect(editorUiState.currentTool) {
        if (editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.INSERT_IMAGE) {
            imagePickerLauncher.launch("image/*")
        }
    }

    // Show snackbar for export result
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(editorUiState.exportResult) {
        editorUiState.exportResult?.let { result ->
            snackbarHostState.showSnackbar(
                message = "Saved: ${java.io.File(result).name}",
                duration = SnackbarDuration.Short
            )
            editorViewModel.clearExportResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var showMenu by remember { mutableStateOf(false) }
            when {
                editorUiState.isEditMode -> {
                    com.pdf.pdfreader.ui.components.AnnotationTopBarDesign(
                        currentTool = editorUiState.currentTool,
                        currentColor = editorUiState.currentColor,
                        currentStrokeWidth = editorUiState.currentStrokeWidth,
                        canUndo = editorUiState.canUndo,
                        canRedo = editorUiState.canRedo,
                        isExporting = editorUiState.isExporting,
                        onToolChange = editorViewModel::setAnnotationToolWithAutoExtract,
                        onColorClick = { /* handled by bottom bar */ },
                        onStrokeWidthChange = editorViewModel::setAnnotationStrokeWidth,
                        onUndo = editorViewModel::undo,
                        onRedo = editorViewModel::redo,
                        onClose = { editorViewModel.setEditMode(false) },
                        onSave = { editorViewModel.saveAnnotationsToPdf(screenWidthPx) },
                        onSignatureClick = { editorViewModel.setSignatureSheetVisible(true) }
                    )
                }
                uiState.isSearchActive -> {
                    SearchTopBar(
                        query = uiState.searchQuery,
                        matchCount = uiState.totalMatchCount,
                        currentMatch = uiState.currentMatchIndex,
                        onQueryChange = viewModel::updateSearchQuery,
                        onNext = viewModel::navigateToNextMatch,
                        onPrevious = viewModel::navigateToPreviousMatch,
                        onClose = viewModel::toggleSearch
                    )
                }
                else -> {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.fileName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::toggleSearch) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_in_pdf))
                            }
                            IconButton(onClick = { showViewOptions = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "View Options")
                            }
                            IconButton(onClick = { editorViewModel.setEditMode(true) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit / Annotate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        bottomBar = {
            var showBottomColorPicker by remember { mutableStateOf(false) }
            
            if (showBottomColorPicker) {
                com.pdf.pdfreader.ui.components.ColorSelectionDialog(
                    initialColor = editorUiState.currentColor,
                    onColorSelected = { editorViewModel.setAnnotationColor(it) },
                    onDismiss = { showBottomColorPicker = false }
                )
            }
            
            com.pdf.pdfreader.ui.components.EditingBottomBar(
                visible = editorUiState.isEditMode,
                currentTool = editorUiState.currentTool,
                currentColor = editorUiState.currentColor,
                currentStrokeWidth = editorUiState.currentStrokeWidth,
                canUndo = editorUiState.canUndo,
                canRedo = editorUiState.canRedo,
                onToolChange = editorViewModel::setAnnotationToolWithAutoExtract,
                onSignatureClick = { editorViewModel.setSignatureSheetVisible(true) },
                onUndoClick = editorViewModel::undo,
                onRedoClick = editorViewModel::redo,
                onColorClick = { showBottomColorPicker = true },
                onStrokeWidthChange = editorViewModel::setAnnotationStrokeWidth
            )
        }
    ) { paddingValues ->
        val pageBgColor = when (viewSettings.backgroundMode) {
            BackgroundMode.ORIGINAL -> Color(0xFFF5F5F5)
            BackgroundMode.PAPER -> Color(0xFFF5F0E1)
            BackgroundMode.EYE_COMFORT -> Color(0xFFF8E8C8)
            BackgroundMode.INVERT -> Color.Black
        }
        val pageContentColorMatrix = remember(viewSettings.backgroundMode) {
            when (viewSettings.backgroundMode) {
                BackgroundMode.PAPER -> androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 10f,
                    0f, 0.97f, 0f, 0f, 5f,
                    0f, 0f, 0.90f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                BackgroundMode.EYE_COMFORT -> androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 20f,
                    0f, 0.93f, 0f, 0f, 10f,
                    0f, 0f, 0.80f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                else -> null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBgColor)
                .then(
                    // Two-finger pinch-to-zoom from the normal reading view.
                    // Enabled whenever we're NOT in annotation edit mode (currentTool
                    // defaults to PEN even while just reading, so it must NOT gate this).
                    // While 2 fingers are down we flip `multiTouch` to disable the
                    // LazyColumn's scrolling, removing the gesture competition that
                    // previously made direct pinch fail until after a double-tap.
                    if (!editorUiState.isEditMode && editorUiState.selectedImageId == null) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    // Initial (top-down) pass: claim the pinch before the
                                    // child list can treat it as a scroll.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.count { it.pressed }
                                    multiTouch = pressed >= 2
                                    if (pressed >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            val maxX = (size.width * (newScale - 1f)) / 2f
                                            val maxY = (size.height * (newScale - 1f)) / 2f
                                            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f; offsetY = 0f
                                        }
                                        // Consume so the list never scrolls during a pinch.
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                multiTouch = false
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!uiState.isLoading && uiState.totalPages > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    // Disable scroll when:
                    // - User is actively interacting with image/text overlays
                    // - Edit mode is active with an interactive tool
                    // - Zoomed in (handled by existing pan gesture)
                    val userScrollEnabled = (!editorUiState.isEditMode
                            || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.NONE)
                            && scale <= 1f
                            && !multiTouch
                            && editorUiState.interactionMode == com.pdf.pdfreader.domain.model.InteractionMode.NONE

                    val pageContent: @Composable (Int) -> Unit = { pageIndex ->
                        PdfPage(
                            pageIndex = pageIndex,
                            viewModel = viewModel,
                            editorViewModel = editorViewModel,
                            width = screenWidthPx,
                            pageStates = pageStates,
                            isScrolling = isScrolling,
                            reloadTrigger = uiState.reloadTrigger,
                            pageContentColorMatrix = pageContentColorMatrix,
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }

                    when (viewSettings.readingMode) {
                        ReadingMode.VERTICAL -> {
                            LazyColumn(
                                state = scrollState,
                                userScrollEnabled = userScrollEnabled,
                                flingBehavior = if (viewSettings.isPageSnap) {
                                    androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(scrollState)
                                } else {
                                    androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.totalPages, key = { it }) { pageIndex ->
                                    pageContent(pageIndex)
                                }
                            }
                        }
                        ReadingMode.HORIZONTAL -> {
                            LazyRow(
                                state = scrollState,
                                userScrollEnabled = userScrollEnabled,
                                flingBehavior = if (viewSettings.isPageSnap) {
                                    androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(scrollState)
                                } else {
                                    androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.totalPages, key = { it }) { pageIndex ->
                                    Box(modifier = Modifier.fillParentMaxSize()) {
                                        pageContent(pageIndex)
                                    }
                                }
                            }
                        }
                    }

                    // ─── GLOBAL Image Overlay (ABOVE all pages) ───────
                    // This is the architectural fix: images render in a SINGLE
                    // layer ABOVE the LazyColumn, so they never go behind
                    // subsequent pages when dragged across page boundaries.
                    if (editorUiState.imageElements.isNotEmpty()) {
                        // An inserted image/signature stays interactive (tap-to-select, drag,
                        // resize) UNLESS a drawing/text tool is actively in use. This means a
                        // placed image can always be re-selected by tapping it — even after
                        // deselecting, and even when not in explicit edit mode — while drawing
                        // tools can still paint over an image without it stealing the touch.
                        val drawingToolActive = editorUiState.isEditMode && (
                            editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.PEN
                                || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.HIGHLIGHTER
                                || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.ERASER
                                || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.TEXT
                                || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.EDIT_TEXT)
                        val imagesInteractive = !drawingToolActive

                        com.pdf.pdfreader.ui.components.GlobalImageOverlay(
                            modifier = Modifier.fillMaxSize(),
                            scrollState = scrollState,
                            imageElements = editorUiState.imageElements,
                            selectedImageId = editorUiState.selectedImageId,
                            selectedImageIds = editorUiState.selectedImageIds,
                            isImageMode = imagesInteractive,
                            onSelectImage = { editorViewModel.selectImage(it) },
                            onMoveImage = { id, delta -> editorViewModel.moveImage(id, delta) },
                            onResizeImage = { id, handle, delta -> editorViewModel.resizeImage(id, handle, delta) },
                            onResizeGroup = { ids, handle, delta, groupW, groupH -> 
                                editorViewModel.resizeGroup(ids, handle, delta, groupW, groupH) 
                            },
                            onResizeEnd = { id -> editorViewModel.onResizeEnd(id) },
                            onResizeGroupEnd = { ids -> editorViewModel.onResizeGroupEnd(ids) },
                            onMoveEnd = { id ->
                                // On move end, detect page boundary crossing
                                editorViewModel.detectPageBoundaryAfterMove(id, scrollState)
                                editorViewModel.onMoveEnd(id)
                            },
                            onInteractionStart = { editorViewModel.setInteractionMode(com.pdf.pdfreader.domain.model.InteractionMode.DRAG) },
                            onInteractionEnd = { editorViewModel.setInteractionMode(com.pdf.pdfreader.domain.model.InteractionMode.NONE) }
                        )

                        // ─── Context-Aware Element Toolbar (dispatched by SelectedElementType) ───
                        val selectedImage = editorUiState.imageElements.find { it.id == editorUiState.selectedImageId }
                        when (editorUiState.selectedElementType) {
                            com.pdf.pdfreader.ui.viewmodel.SelectedElementType.SIGNATURE -> {
                                if (selectedImage != null) {
                                    val pageLayouts = com.pdf.pdfreader.ui.components.rememberVisiblePageLayouts(scrollState)
                                    val pageLayout = pageLayouts.find { it.pageIndex == selectedImage.pageIndex }
                                    if (pageLayout != null) {
                                        val sigAnchorTop = pageLayout.offsetInViewport + selectedImage.position.y.toInt()
                                        val sigW = selectedImage.width * selectedImage.scale
                                        val sigH = selectedImage.height * selectedImage.scale
                                        val sigAnchorCenterX = (selectedImage.position.x + sigW / 2f).toInt()
                                        val sigAnchorBottom = sigAnchorTop + sigH.toInt()
                                        val sigColor = selectedImage.signatureStrokes?.firstOrNull()?.let {
                                            androidx.compose.ui.graphics.Color(it.color.toULong())
                                        } ?: Color.Black
                                        val sigStrokeWidth = selectedImage.signatureStrokes?.firstOrNull()?.strokeWidth ?: 5f

                                        Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                            com.pdf.pdfreader.ui.components.SignatureEditToolbar(
                                                visible = true,
                                                anchorCenterX = sigAnchorCenterX,
                                                anchorTop = sigAnchorTop,
                                                anchorBottom = sigAnchorBottom,
                                                isLocked = selectedImage.isLocked,
                                                opacity = selectedImage.opacity,
                                                currentStrokeWidth = sigStrokeWidth,
                                                currentColor = sigColor,
                                                hasEditableStrokes = selectedImage.signatureStrokes != null,
                                                onStrokeWidthChange = { newWidth ->
                                                    editorViewModel.updateSignatureProperties(
                                                        selectedImage.id,
                                                        newStrokeWidth = newWidth
                                                    )
                                                },
                                                onColorChange = { newColor ->
                                                    editorViewModel.updateSignatureProperties(
                                                        selectedImage.id,
                                                        newColor = newColor
                                                    )
                                                },
                                                onRotateLeft = { editorViewModel.rotateImage(selectedImage.id, -90f) },
                                                onRotateRight = { editorViewModel.rotateImage(selectedImage.id, 90f) },
                                                onDelete = { editorViewModel.deleteImage(selectedImage.id) },
                                                onDuplicate = { editorViewModel.duplicateImage(selectedImage.id) },
                                                onBringToFront = { editorViewModel.bringToFront(selectedImage.id) },
                                                onSendToBack = { editorViewModel.sendToBack(selectedImage.id) },
                                                onToggleLock = { editorViewModel.toggleImageLock(selectedImage.id) },
                                                onOpacityChange = { editorViewModel.setImageOpacity(selectedImage.id, it) }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            com.pdf.pdfreader.ui.viewmodel.SelectedElementType.IMAGE -> {
                                if (selectedImage != null) {
                                    val pageLayouts = com.pdf.pdfreader.ui.components.rememberVisiblePageLayouts(scrollState)
                                    val pageLayout = pageLayouts.find { it.pageIndex == selectedImage.pageIndex }
                                    if (pageLayout != null) {
                                        val imgAnchorTop = pageLayout.offsetInViewport + selectedImage.position.y.toInt()
                                        val imgW = selectedImage.width * selectedImage.scale
                                        val imgH = selectedImage.height * selectedImage.scale
                                        val imgAnchorCenterX = (selectedImage.position.x + imgW / 2f).toInt()
                                        val imgAnchorBottom = imgAnchorTop + imgH.toInt()
                                        Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                            com.pdf.pdfreader.ui.components.ImageEditToolbar(
                                                visible = true,
                                                anchorCenterX = imgAnchorCenterX,
                                                anchorTop = imgAnchorTop,
                                                anchorBottom = imgAnchorBottom,
                                                isLocked = selectedImage.isLocked,
                                                opacity = selectedImage.opacity,
                                                onRotateLeft = { editorViewModel.rotateImage(selectedImage.id, -90f) },
                                                onRotateRight = { editorViewModel.rotateImage(selectedImage.id, 90f) },
                                                onDelete = { editorViewModel.deleteImage(selectedImage.id) },
                                                onDuplicate = { editorViewModel.duplicateImage(selectedImage.id) },
                                                onBringToFront = { editorViewModel.bringToFront(selectedImage.id) },
                                                onSendToBack = { editorViewModel.sendToBack(selectedImage.id) },
                                                onToggleLock = { editorViewModel.toggleImageLock(selectedImage.id) },
                                                onOpacityChange = { editorViewModel.setImageOpacity(selectedImage.id, it) },
                                                onSnapToCenter = {
                                                    editorViewModel.snapImageToCenter(
                                                        selectedImage.id,
                                                        screenWidthPx,
                                                        pageLayout.height
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            com.pdf.pdfreader.ui.viewmodel.SelectedElementType.GROUP -> {
                                val groupElements = editorUiState.imageElements.filter { editorUiState.selectedImageIds.contains(it.id) }
                                val minX = groupElements.minOfOrNull { it.position.x } ?: 0f
                                val minY = groupElements.minOfOrNull { it.position.y } ?: 0f
                                val topPageIdx = groupElements.minOfOrNull { it.pageIndex } ?: 0
                                
                                val pageLayouts = com.pdf.pdfreader.ui.components.rememberVisiblePageLayouts(scrollState)
                                val pageLayout = pageLayouts.find { it.pageIndex == topPageIdx }
                                
                                if (pageLayout != null) {
                                    val grpMaxRight = groupElements.maxOfOrNull { it.position.x + it.width * it.scale } ?: minX
                                    val grpMaxBottom = groupElements.maxOfOrNull { it.position.y + it.height * it.scale } ?: minY
                                    val grpAnchorTop = pageLayout.offsetInViewport + minY.toInt()
                                    val grpAnchorBottom = pageLayout.offsetInViewport + grpMaxBottom.toInt()
                                    val grpAnchorCenterX = ((minX + grpMaxRight) / 2f).toInt()
                                    Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                        com.pdf.pdfreader.ui.components.ImageEditToolbar(
                                            visible = true,
                                            anchorCenterX = grpAnchorCenterX,
                                            anchorTop = grpAnchorTop,
                                            anchorBottom = grpAnchorBottom,
                                            isLocked = false,
                                            opacity = 1f,
                                            onRotateLeft = { },
                                            onRotateRight = { },
                                            onDelete = { 
                                                editorUiState.selectedImageIds.forEach { editorViewModel.deleteImage(it) }
                                                editorViewModel.selectImage(null)
                                            },
                                            onDuplicate = { 
                                                editorUiState.selectedImageIds.forEach { editorViewModel.duplicateImage(it) } 
                                            },
                                            onBringToFront = { 
                                                editorUiState.selectedImageIds.forEach { editorViewModel.bringToFront(it) } 
                                            },
                                            onSendToBack = { 
                                                editorUiState.selectedImageIds.forEach { editorViewModel.sendToBack(it) } 
                                            },
                                            onToggleLock = { 
                                                editorUiState.selectedImageIds.forEach { editorViewModel.toggleImageLock(it) } 
                                            },
                                            onOpacityChange = { op ->
                                                editorUiState.selectedImageIds.forEach { editorViewModel.setImageOpacity(it, op) }
                                            },
                                            onSnapToCenter = { }
                                        )
                                    }
                                }
                            }
                            
                            // TEXT and NONE — no image/signature toolbar needed
                            else -> { }
                        }
                    }

                    // ─── Text Selection Toolbar ───────
                    val textSel = editorUiState.textSelection
                    if (textSel != null && textSel.bounds != null) {
                        val pageLayouts = com.pdf.pdfreader.ui.components.rememberVisiblePageLayouts(scrollState)
                        val pageLayout = pageLayouts.find { it.pageIndex == textSel.pageIndex }
                        if (pageLayout != null) {
                            val toolbarGlobalY = pageLayout.offsetInViewport + textSel.bounds.top.toInt() - 60
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                com.pdf.pdfreader.ui.components.TextSelectionToolbar(
                                    visible = true,
                                    offsetX = textSel.bounds.left.toInt(),
                                    offsetY = toolbarGlobalY.coerceAtLeast(0),
                                    onCopy = { editorViewModel.copySelectedText(context) },
                                    onEdit = { editorViewModel.editSelectedText() },
                                    onHighlight = { editorViewModel.annotateSelectedText(com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.HIGHLIGHT) },
                                    onUnderline = { editorViewModel.annotateSelectedText(com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.UNDERLINE) },
                                    onStrikethrough = { editorViewModel.annotateSelectedText(com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.STRIKETHROUGH) }
                                )
                            }
                        }
                    }
                }

                // Pan + un-zoom overlay — only visible when zoomed
                if (scale > 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f; offsetX = 0f; offsetY = 0f
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                // While zoomed, list scroll is disabled, so we can use
                                // a full transform gesture: pinch to zoom further or
                                // out, and single-finger drag to pan.
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                    scale = newScale
                                    if (newScale > 1f) {
                                        val maxX = (size.width * (newScale - 1f)) / 2f
                                        val maxY = (size.height * (newScale - 1f)) / 2f
                                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    } else {
                                        offsetX = 0f; offsetY = 0f
                                    }
                                }
                            }
                    )
                }
            }

            // Right-side draggable page scrollbar
            com.pdf.pdfreader.ui.components.PageScrollbar(
                visible = isSliderVisible,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                onPageChange = { page ->
                    sliderInteractionTime = System.currentTimeMillis()
                    viewModel.updateCurrentPage(page)
                    coroutineScope.launch { scrollState.scrollToItem(page) }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // Floating page indicator pill at bottom-center
            if (uiState.totalPages > 0 && !editorUiState.isEditMode) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSliderVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xDD1C1B1F),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Page ${uiState.currentPage + 1} of ${uiState.totalPages}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }

            if (uiState.isPasswordPromptVisible) {
                PasswordPromptDialog(
                    fileName = uiState.fileName,
                    password = uiState.password,
                    isError = !uiState.isPasswordCorrect,
                    onPasswordChange = { viewModel.onPasswordChange(it) },
                    onDismiss = onNavigateBack,
                    onSubmit = { viewModel.submitPassword(uiState.password) }
                )
            }

            uiState.errorMessage?.let { message ->
                ErrorView(
                    message = message,
                    onRetry = { viewModel.initialize(uiState.filePath) },
                    onExit = onNavigateBack
                )
            }
        }

        // ─── Keep Screen On ─────────────────────────────────────
        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        DisposableEffect(viewSettings.keepScreenOn) {
            if (viewSettings.keepScreenOn) {
                activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // ─── View Options Bottom Sheet ──────────────────────────
        if (showViewOptions) {
            ViewOptionsSheet(
                viewSettings = viewSettings,
                onSettingsChange = { viewModel.updateViewSettings(it) },
                onManagePages = { 
                    showViewOptions = false
                    onNavigateToManagePages(uiState.filePath)
                },
                onDismiss = { showViewOptions = false }
            )
        }

        // ─── Signature UIs ───────────────────────────────────────
        com.pdf.pdfreader.ui.components.SignatureBottomSheet(
            visible = editorUiState.isSignatureSheetVisible,
            savedSignatures = editorUiState.savedSignatures,
            onDismissRequest = { editorViewModel.setSignatureSheetVisible(false) },
            onCreateNewSignature = { 
                editorViewModel.setSignatureSheetVisible(false)
                editorViewModel.setSignaturePadVisible(true)
            },
            onSelectSignature = { editorViewModel.insertSignatureAsImage(it) },
            onDeleteSignature = { editorViewModel.deleteSignature(it) }
        )

        if (editorUiState.isSignaturePadVisible) {
            com.pdf.pdfreader.ui.components.SignaturePadDialog(
                onDismissRequest = { editorViewModel.setSignaturePadVisible(false) },
                onSaveSignature = { strokes, w, h -> editorViewModel.saveSignature(strokes, w, h) }
            )
        }
    }
}
