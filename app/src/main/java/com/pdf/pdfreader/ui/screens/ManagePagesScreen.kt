package com.pdf.pdfreader.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.ui.viewmodel.ManagePagesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePagesScreen(
    viewModel: ManagePagesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Width for thumbnails based on grid logic: 3 columns with padding
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    val density = LocalDensity.current.density
    val thumbWidthPx = (screenWidth / 3f).toInt()

    var reorderMode by remember { mutableStateOf(false) }
    // Working page order while reordering; reset whenever the document reloads.
    var orderedPages by remember(uiState.totalPages, uiState.reloadTrigger) {
        mutableStateOf((0 until uiState.totalPages).toList())
    }

    BackHandler {
        when {
            reorderMode -> reorderMode = false
            uiState.selectedPages.isNotEmpty() -> viewModel.clearSelection()
            else -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            reorderMode -> "Reorder Pages"
                            uiState.selectedPages.isNotEmpty() -> "${uiState.selectedPages.size} Selected"
                            else -> "Manage Pages"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                reorderMode -> reorderMode = false
                                uiState.selectedPages.isNotEmpty() -> viewModel.clearSelection()
                                else -> onNavigateBack()
                            }
                        }
                    ) {
                        val icon = if (uiState.selectedPages.isNotEmpty() && !reorderMode)
                            Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowBack
                        Icon(icon, contentDescription = "Back")
                    }
                },
                actions = {
                    if (reorderMode) {
                        // Persist the new order.
                        IconButton(onClick = {
                            viewModel.reorderPages(orderedPages) { success ->
                                reorderMode = false
                                if (success) Toast.makeText(context, "Pages reordered", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Done")
                        }
                    } else if (PdfEditorFeatureConfig.ENABLE_REORDER_PAGE &&
                        uiState.selectedPages.isEmpty() && uiState.totalPages > 1
                    ) {
                        IconButton(onClick = {
                            viewModel.reversePages { success ->
                                if (success) Toast.makeText(context, "Pages reversed", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ImportExport, contentDescription = "Reverse page order")
                        }
                        IconButton(onClick = {
                            orderedPages = (0 until uiState.totalPages).toList()
                            reorderMode = true
                        }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Reorder pages")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        bottomBar = {
            if (uiState.selectedPages.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Page-management actions are gated by PdfEditorFeatureConfig.
                        if (PdfEditorFeatureConfig.ENABLE_INSERT_PAGE) {
                            ActionItem(
                                icon = Icons.Default.Add,
                                label = "Insert",
                                onClick = {
                                    viewModel.insertBlankPage { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "Blank page inserted" else "Insert failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            ActionItem(
                                icon = Icons.Default.ContentCopy,
                                label = "Duplicate",
                                onClick = {
                                    viewModel.duplicateSelectedPages { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "Pages duplicated" else "Duplicate failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                        if (PdfEditorFeatureConfig.ENABLE_ROTATE_PAGE) {
                            ActionItem(
                                icon = Icons.Default.RotateRight,
                                label = "Rotate",
                                onClick = {
                                    viewModel.rotateSelectedPages(90) { success ->
                                        if (success) Toast.makeText(context, "Pages rotated", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        if (PdfEditorFeatureConfig.ENABLE_EXTRACT_PAGE) {
                            ActionItem(
                                icon = Icons.Default.FileDownload,
                                label = "Extract",
                                onClick = {
                                    val downloadsStr = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                                    viewModel.extractSelectedPages(downloadsStr) { path ->
                                        if (path != null) {
                                            Toast.makeText(context, "Extracted to Downloads", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                        if (PdfEditorFeatureConfig.ENABLE_DELETE_PAGE) {
                            ActionItem(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                onClick = {
                                    viewModel.deleteSelectedPages { success ->
                                        if (success) Toast.makeText(context, "Pages deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (reorderMode && uiState.totalPages > 0) {
                ReorderablePageGrid(
                    viewModel = viewModel,
                    reloadTrigger = uiState.reloadTrigger,
                    orderedPages = orderedPages,
                    onOrderChange = { orderedPages = it },
                    thumbWidthPx = thumbWidthPx
                )
            } else if (uiState.totalPages > 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.totalPages, key = { it }) { pageIndex ->
                        var thumb by remember { mutableStateOf<Bitmap?>(null) }
                        
                        LaunchedEffect(pageIndex, uiState.reloadTrigger) {
                            thumb = viewModel.getThumbnail(pageIndex, thumbWidthPx)
                        }
                        
                        val isSelected = uiState.selectedPages.contains(pageIndex)
                        
                        PageThumbnail(
                            bitmap = thumb,
                            pageIndex = pageIndex,
                            isSelected = isSelected,
                            onClick = { viewModel.toggleSelection(pageIndex) }
                        )
                    }
                }
            } else {
                Text("No pages available")
            }
            
            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

/**
 * A 3-column thumbnail grid that supports long-press drag to reorder pages.
 *
 * [orderedPages] holds the current visual order as original 0-based page indices. While a page is
 * dragged, the item under the pointer is found via the grid's [androidx.compose.foundation.lazy.grid.LazyGridState]
 * layout info and the list is reordered live (items animate to their new slots via [Modifier.animateItem]).
 * The gesture's `pointerInput` is keyed on `Unit` so a live reorder doesn't cancel the drag; the
 * latest order is read through [rememberUpdatedState].
 */
@Composable
private fun ReorderablePageGrid(
    viewModel: ManagePagesViewModel,
    reloadTrigger: Int,
    orderedPages: List<Int>,
    onOrderChange: (List<Int>) -> Unit,
    thumbWidthPx: Int
) {
    val gridState = rememberLazyGridState()
    var draggingPos by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val currentOrder by rememberUpdatedState(orderedPages)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(orderedPages.size, key = { orderedPages[it] }) { pos ->
            val originalIndex = orderedPages[pos]
            var thumb by remember(originalIndex) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(originalIndex, reloadTrigger) {
                thumb = viewModel.getThumbnail(originalIndex, thumbWidthPx)
            }
            val isDragging = draggingPos == pos

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .animateItem()
                    .graphicsLayer {
                        if (isDragging) { alpha = 0.85f; scaleX = 1.06f; scaleY = 1.06f }
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                val from = currentOrder.indexOf(originalIndex)
                                draggingPos = from
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
                                pointer = Offset(
                                    (info?.offset?.x ?: 0).toFloat(),
                                    (info?.offset?.y ?: 0).toFloat()
                                ) + local
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                pointer += dragAmount
                                val from = draggingPos ?: return@detectDragGesturesAfterLongPress
                                val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    pointer.x >= item.offset.x && pointer.x <= item.offset.x + item.size.width &&
                                        pointer.y >= item.offset.y && pointer.y <= item.offset.y + item.size.height
                                }?.index
                                if (target != null && target != from && target in currentOrder.indices) {
                                    val newList = currentOrder.toMutableList().apply { add(target, removeAt(from)) }
                                    onOrderChange(newList)
                                    draggingPos = target
                                }
                            },
                            onDragEnd = { draggingPos = null },
                            onDragCancel = { draggingPos = null }
                        )
                    }
            ) {
                ReorderThumbnail(bitmap = thumb, slotNumber = pos + 1, isDragging = isDragging)
            }
        }
    }
}

@Composable
private fun ReorderThumbnail(bitmap: Bitmap?, slotNumber: Int, isDragging: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    width = if (isDragging) 3.dp else 1.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary else Color.LightGray,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$slotNumber",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun PageThumbnail(
    bitmap: Bitmap?,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}
