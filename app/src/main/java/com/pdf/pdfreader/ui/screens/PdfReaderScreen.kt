package com.pdf.pdfreader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

    // ─── Crisp-zoom: re-render the focused page at the zoomed resolution ───
    // graphicsLayer only magnifies the existing bitmap (blurry). When the user
    // zooms in we render a fresh, higher-resolution bitmap for the current page
    // so text/graphics stay sharp — similar to Google Drive's PDF viewer.
    LaunchedEffect(scale, uiState.currentPage) {
        if (scale > 1f) {
            // debounce so we don't render mid-gesture / during the zoom animation
            delay(180)
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
                    // Slim edit-mode top bar: Close + Save/Export only
                    // All tools are in the bottom EditingBottomBar — no duplication
                    TopAppBar(
                        title = {
                            Text(
                                text = "Edit Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { editorViewModel.setEditMode(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Edit Mode")
                            }
                        },
                        actions = {
                            // Stroke width slider toggle (only for pen/highlighter)
                            if (editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.PEN
                                || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.HIGHLIGHTER
                            ) {
                                Text(
                                    text = "${editorUiState.currentStrokeWidth.toInt()}px",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Export (visible when overlays exist)
                            if (editorUiState.hasEditableOverlays) {
                                IconButton(
                                    onClick = { editorViewModel.exportEditedPdf(screenWidthPx) },
                                    enabled = !editorUiState.isExporting
                                ) {
                                    if (editorUiState.isExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "Export Edited PDF",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            // Save annotations
                            IconButton(onClick = { editorViewModel.saveAnnotationsToPdf(screenWidthPx) }) {
                                Icon(Icons.Default.Save, contentDescription = "Save Annotations")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    // Stroke width slider (expandable below top bar for pen/highlighter)
                    if (editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.PEN
                        || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.HIGHLIGHTER
                    ) {
                        com.pdf.pdfreader.ui.components.StrokeWidthSliderExpandable(
                            currentStrokeWidth = editorUiState.currentStrokeWidth,
                            currentColor = editorUiState.currentColor,
                            onStrokeWidthChange = editorViewModel::setAnnotationStrokeWidth
                        )
                    }
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
                            Column {
                                Text(
                                    text = uiState.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (uiState.totalPages > 0) {
                                    val percent = ((uiState.currentPage + 1).toFloat() / uiState.totalPages * 100).toInt()
                                    Text(
                                        text = "${stringResource(R.string.page)} ${uiState.currentPage + 1} ${stringResource(R.string.of)} ${uiState.totalPages}  •  $percent%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                            }
                        },
                        actions = {
                            // Search button
                            IconButton(onClick = viewModel::toggleSearch) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_in_pdf))
                            }
                            // Bookmark button
                            IconButton(onClick = viewModel::toggleBookmark) {
                                Icon(
                                    imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Overflow menu
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit / Annotate") },
                                    onClick = { 
                                        showMenu = false
                                        editorViewModel.setEditMode(true) 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Options") },
                                    onClick = { 
                                        showMenu = false
                                        showViewOptions = true
                                    },
                                    leadingIcon = { 
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
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
                canUndo = editorUiState.canUndo,
                canRedo = editorUiState.canRedo,
                onToolChange = editorViewModel::setAnnotationToolWithAutoExtract,
                onSignatureClick = { editorViewModel.setSignatureSheetVisible(true) },
                onUndoClick = editorViewModel::undo,
                onRedoClick = editorViewModel::redo,
                onColorClick = { showBottomColorPicker = true }
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
                .background(pageBgColor),
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
                        com.pdf.pdfreader.ui.components.GlobalImageOverlay(
                            modifier = Modifier.fillMaxSize(),
                            scrollState = scrollState,
                            imageElements = editorUiState.imageElements,
                            selectedImageId = editorUiState.selectedImageId,
                            selectedImageIds = editorUiState.selectedImageIds,
                            isImageMode = editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.INSERT_IMAGE
                                    || editorUiState.selectedImageId != null,
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
                                        val toolbarGlobalY = pageLayout.offsetInViewport + selectedImage.position.y.toInt() - 60
                                        val sigColor = selectedImage.signatureStrokes?.firstOrNull()?.let {
                                            androidx.compose.ui.graphics.Color(it.color.toULong())
                                        } ?: Color.Black
                                        val sigStrokeWidth = selectedImage.signatureStrokes?.firstOrNull()?.strokeWidth ?: 5f
                                        
                                        Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                            com.pdf.pdfreader.ui.components.SignatureEditToolbar(
                                                visible = true,
                                                offsetX = selectedImage.position.x.toInt(),
                                                offsetY = toolbarGlobalY.coerceAtLeast(0),
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
                                        val toolbarGlobalY = pageLayout.offsetInViewport + selectedImage.position.y.toInt() - 60
                                        Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                            com.pdf.pdfreader.ui.components.ImageEditToolbar(
                                                visible = true,
                                                offsetX = selectedImage.position.x.toInt(),
                                                offsetY = toolbarGlobalY.coerceAtLeast(0),
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
                                    val toolbarGlobalY = pageLayout.offsetInViewport + minY.toInt() - 60
                                    Box(modifier = Modifier.fillMaxSize().zIndex(200f)) {
                                        com.pdf.pdfreader.ui.components.ImageEditToolbar(
                                            visible = true,
                                            offsetX = minX.toInt(),
                                            offsetY = toolbarGlobalY.coerceAtLeast(0),
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
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val maxX = (size.width * (scale - 1f)) / 2f
                                    val maxY = (size.height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + dragAmount.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + dragAmount.y).coerceIn(-maxY, maxY)
                                }
                            }
                    )
                }
            }

            // Page slider
            if (uiState.totalPages > 1) {
                AnimatedVisibility(
                    visible = isSliderVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Slider(
                            value = uiState.currentPage.toFloat(),
                            onValueChange = { page ->
                                sliderInteractionTime = System.currentTimeMillis()
                                viewModel.updateCurrentPage(page.toInt())
                                coroutineScope.launch {
                                    scrollState.scrollToItem(page.toInt())
                                }
                            },
                            valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                            steps = if (uiState.totalPages > 2) uiState.totalPages - 2 else 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
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

// ─── Search Top Bar ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    matchCount: Int,
    currentMatch: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search input field
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_pdf_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Match count
                if (matchCount > 0 && currentMatch >= 0) {
                    Text(
                        text = "${currentMatch + 1}/$matchCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else if (query.isNotEmpty() && matchCount == 0) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Navigation arrows
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ─── PDF Page Composable ────────────────────────────────────────

/**
 * State-driven PDF page composable.
 * Observes PageRenderState from the ViewModel — no suspend calls in Composable.
 * Shows:  Loading → spinner,  Success → bitmap,  Error → retry button
 */
@Composable
fun PdfPage(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel,
    width: Int,
    pageStates: Map<Int, PageRenderState>,
    isScrolling: Boolean = false,
    reloadTrigger: Int = 0,
    pageContentColorMatrix: androidx.compose.ui.graphics.ColorMatrix? = null,
    onDoubleTap: () -> Unit = {}
) {
    val renderState = pageStates[pageIndex]
    val currentIsScrolling by rememberUpdatedState(isScrolling)

    // Request render when page becomes visible (or after reload)
    LaunchedEffect(pageIndex, width, reloadTrigger) {
        if (currentIsScrolling) delay(150)
        viewModel.requestPageRender(pageIndex, width)
    }

    // Re-request when scrolling stops if page wasn't rendered yet
    LaunchedEffect(isScrolling) {
        if (!isScrolling) {
            delay(80)
            val state = pageStates[pageIndex]
            if (state !is PageRenderState.Success) {
                viewModel.requestPageRender(pageIndex, width)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val pageBg = when (uiState.viewSettings.backgroundMode) {
        BackgroundMode.ORIGINAL -> Color.White
        BackgroundMode.PAPER -> Color(0xFFF5F0E1)
        BackgroundMode.EYE_COMFORT -> Color(0xFFF8E8C8)
        BackgroundMode.INVERT -> Color.Black
    }
    // Only intercept taps for annotation deselect when NOT in text/image editing mode.
    // CRITICAL: When EDIT_TEXT, INSERT_IMAGE, or image selected, do NOT attach
    // any tap handler at the page level — let events pass through to overlay children.
    val isInteractiveEditMode = editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.EDIT_TEXT
            || editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.INSERT_IMAGE
            || editorUiState.selectedImageId != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(pageBg)
            .then(
                if (!isInteractiveEditMode && !editorUiState.isEditMode) {
                    // Reading mode: double-tap to zoom, single-tap deselect
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onDoubleTap() },
                            onTap = { editorViewModel.selectAnnotation(null) }
                        )
                    }
                } else if (!isInteractiveEditMode) {
                    // Edit mode with drawing tool: deselect + double-tap
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onDoubleTap() },
                            onTap = { editorViewModel.selectAnnotation(null) }
                        )
                    }
                } else {
                    // Interactive overlay mode (EDIT_TEXT / INSERT_IMAGE / image selected):
                    // Do NOT attach tap gesture — let children handle all events
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (renderState) {
            is PageRenderState.Success -> {
                val bitmap = renderState.bitmap
                if (!bitmap.isRecycled) {
                    var pageSize by remember { mutableStateOf(IntSize.Zero) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { pageSize = it }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                            colorFilter = if (pageContentColorMatrix != null) androidx.compose.ui.graphics.ColorFilter.colorMatrix(pageContentColorMatrix) else null
                        )

                        // Search highlights overlay
                        if (uiState.isSearchActive && uiState.searchResults.isNotEmpty() && pageSize != IntSize.Zero) {
                            val highlights = remember(uiState.currentMatchIndex, uiState.searchResults, pageSize) {
                                viewModel.getSearchHighlightsForPage(pageIndex, pageSize.width, pageSize.height)
                            }

                            if (highlights.isNotEmpty()) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    highlights.forEach { (rect, isCurrent) ->
                                        drawRect(
                                            color = if (isCurrent) Color(0xFFFF9800).copy(alpha = 0.5f)
                                            else Color.Yellow.copy(alpha = 0.35f),
                                            topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                                            size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                                        )
                                    }
                                }
                            }
                        }

                        // Annotation overlay
                        PdfAnnotationOverlay(
                            modifier = Modifier.matchParentSize(),
                            isEditMode = editorUiState.isEditMode,
                            currentTool = editorUiState.currentTool,
                            currentColor = editorUiState.currentColor,
                            currentStrokeWidth = editorUiState.currentStrokeWidth,
                            annotations = editorUiState.annotations,
                            onAnnotationAdded = editorViewModel::addAnnotation,
                            onAnnotationRemoved = editorViewModel::removeAnnotation,
                            pageIndex = pageIndex
                        )

                        // Text annotations
                        editorUiState.annotations
                            .filterIsInstance<com.pdf.pdfreader.domain.model.PdfAnnotation.TextNote>()
                            .filter { it.pageIndex == pageIndex }
                            .forEach { textNote ->
                                key(textNote.id) {
                                    com.pdf.pdfreader.ui.components.MovableTextNote(
                                        note = textNote,
                                        isEditMode = editorUiState.isEditMode,
                                        isSelected = editorUiState.selectedAnnotationId == textNote.id,
                                        onSelect = { editorViewModel.selectAnnotation(textNote.id) },
                                        onDeselect = { editorViewModel.selectAnnotation(null) },
                                        onCommit = { before, after -> editorViewModel.commitTextAnnotation(before, after, pageIndex) },
                                        onDelete = { 
                                            editorViewModel.removeAnnotation(textNote.id)
                                            if (editorUiState.selectedAnnotationId == textNote.id) {
                                                editorViewModel.selectAnnotation(null)
                                            }
                                        }
                                    )
                                }
                            }

                        // ─── Text Edit Overlay (Edit existing PDF text) ────
                        if (editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.EDIT_TEXT && pageSize != IntSize.Zero) {
                            val pageTextBlocks = editorUiState.textBlocks[pageIndex] ?: emptyList()
                            com.pdf.pdfreader.ui.components.TextEditOverlay(
                                modifier = Modifier.matchParentSize(),
                                pageIndex = pageIndex,
                                pageSize = pageSize,
                                textBlocks = pageTextBlocks,
                                editedTextBlocks = editorUiState.editedTextBlocks.filter { it.originalBlock.pageIndex == pageIndex },
                                selectedTextBlockId = editorUiState.selectedTextBlockId,
                                isEditTextMode = true,
                                onSelectTextBlock = { editorViewModel.selectTextBlock(it) },
                                onEditTextBlock = { blockId, newText, newFontSize, newColor ->
                                    editorViewModel.editTextBlock(blockId, newText, newFontSize, newColor)
                                }
                            )

                            // Show loading indicator while extracting text
                            if (editorUiState.isTextBlocksLoading) {
                                Box(
                                    modifier = Modifier.matchParentSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }

                        // ─── Text Selection Overlay (Chunk 2, 3) ────
                        if (editorUiState.currentTool == com.pdf.pdfreader.ui.components.AnnotationTool.NONE && !editorUiState.isEditMode && pageSize != IntSize.Zero) {
                            val pageTextBlocks = editorUiState.textBlocks[pageIndex] ?: emptyList()
                            if (pageTextBlocks.isNotEmpty()) {
                                com.pdf.pdfreader.ui.components.TextSelectionOverlay(
                                    modifier = Modifier.matchParentSize(),
                                    pageIndex = pageIndex,
                                    pageWidth = pageSize.width,
                                    pageHeight = pageSize.height,
                                    textBlocks = pageTextBlocks,
                                    interactionMode = editorUiState.interactionMode,
                                    textSelection = editorUiState.textSelection,
                                    editorViewModel = editorViewModel
                                )
                            }
                        }

                        // NOTE: Image overlay has been moved to GlobalImageOverlay
                        // (rendered ABOVE the LazyColumn, not inside per-page containers)
                        // This prevents images from going behind subsequent pages.
                    }
                } else {
                    // Bitmap was recycled — re-request
                    LaunchedEffect(Unit) {
                        viewModel.requestPageRender(pageIndex, width)
                    }
                    PagePlaceholder(isLoading = true)
                }
            }

            is PageRenderState.Error -> {
                PageErrorView(
                    message = renderState.message,
                    onRetry = { viewModel.retryPageRender(pageIndex, width) }
                )
            }

            is PageRenderState.Loading, null -> {
                PagePlaceholder(isLoading = true)
            }
        }
    }
}

// ─── Page Placeholder ───────────────────────────────────────────

@Composable
private fun PagePlaceholder(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.414f)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.rendering),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ─── Page Error View ────────────────────────────────────────────

@Composable
private fun PageErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.414f)
            .background(Color(0xFFFFF3E0))
            .clickable { onRetry() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.render_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.tap_to_retry),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBF360C)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─── Password Dialog ────────────────────────────────────────────

@Composable
fun PasswordPromptDialog(
    fileName: String,
    password: String,
    isError: Boolean,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.enter_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${stringResource(R.string.pdf_locked_desc).replace("PDF", "\"$fileName\"")}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.enter_password)) },
                    isError = isError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = null)
                        }
                    },
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = password.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.open_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ─── Error View ─────────────────────────────────────────────────

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.opening_pdf_failed),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
