package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.R
import com.pdf.pdfreader.ui.components.PdfAnnotationOverlay
import com.pdf.pdfreader.ui.viewmodel.PageRenderState
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import com.pdf.pdfreader.domain.model.BackgroundMode
import kotlinx.coroutines.delay

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
