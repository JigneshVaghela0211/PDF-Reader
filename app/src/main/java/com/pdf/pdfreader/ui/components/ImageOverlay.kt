package com.pdf.pdfreader.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.domain.model.ResizeHandle

private const val TAG = "ImageOverlay"

/**
 * Overlay composable that renders all inserted images on a PDF page.
 *
 * Gesture priority (critical):
 *   1. Resize handles (zIndex = 20)
 *   2. Image body drag (zIndex = 10)
 *   3. Deselect tap catcher (zIndex = 0) — BELOW images, not above
 */
@Composable
fun ImageOverlay(
    modifier: Modifier = Modifier,
    pageIndex: Int,
    pageSize: IntSize,
    imageElements: List<ImageElement>,
    selectedImageId: String?,
    isImageMode: Boolean,
    onSelectImage: (String?) -> Unit,
    onMoveImage: (String, Offset) -> Unit,
    onResizeImage: (String, ResizeHandle, Offset) -> Unit,
    onResizeEnd: (String) -> Unit,
    onMoveEnd: (String) -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {}
) {
    if (pageSize == IntSize.Zero) return

    val pageImages = imageElements.filter { it.pageIndex == pageIndex }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Deselect tap catcher BELOW images (zIndex = 0) ───
        if (isImageMode && selectedImageId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(selectedImageId) {
                        detectTapGestures {
                            Log.d(TAG, "Tap on empty space → deselect")
                            onSelectImage(null)
                        }
                    }
            )
        }

        // ─── Image elements (zIndex = 10+) ───
        pageImages.forEach { element ->
            key(element.id) {
                ImageElementView(
                    element = element,
                    isSelected = element.id == selectedImageId,
                    isInteractive = isImageMode,
                    onSelect = {
                        Log.d(TAG, "Image selected: ${element.id}")
                        onSelectImage(element.id)
                    },
                    onMoveBy = { delta ->
                        Log.d(TAG, "Move ${element.id}: $delta")
                        onMoveImage(element.id, delta)
                    },
                    onResizeByHandle = { handle, delta ->
                        Log.d(TAG, "Resize ${element.id} handle=$handle delta=$delta")
                        onResizeImage(element.id, handle, delta)
                    },
                    onResizeEnd = { onResizeEnd(element.id) },
                    onMoveEnd = { onMoveEnd(element.id) },
                    onInteractionStart = onInteractionStart,
                    onInteractionEnd = onInteractionEnd
                )
            }
        }
    }
}

/**
 * Individual image element with proper gesture hierarchy:
 * - Tap to select
 * - Drag to move (only when selected, consumes events to prevent scroll)
 * - Resize handles drawn above with higher zIndex
 */
@Composable
private fun ImageElementView(
    element: ImageElement,
    isSelected: Boolean,
    isInteractive: Boolean,
    onSelect: () -> Unit,
    onMoveBy: (Offset) -> Unit,
    onResizeByHandle: (ResizeHandle, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onMoveEnd: () -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Load bitmap from URI (cached)
    val bitmap = remember(element.uri) {
        try {
            val uri = Uri.parse(element.uri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            inputStream?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: ${element.uri}", e)
            null
        }
    }

    if (bitmap == null || bitmap.isRecycled) return

    val scaledWidth = element.width * element.scale
    val scaledHeight = element.height * element.scale

    // Image body — zIndex 10
    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 12f else 10f)
            .offset { IntOffset(element.position.x.toInt(), element.position.y.toInt()) }
            .size(
                width = with(density) { scaledWidth.toDp() },
                height = with(density) { scaledHeight.toDp() }
            )
            .graphicsLayer {
                rotationZ = element.rotation
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
            .then(
                if (isInteractive && isSelected) {
                    // Drag gesture — when selected, consume events to prevent parent scroll
                    Modifier.pointerInput(element.id) {
                        detectDragGestures(
                            onDragStart = {
                                Log.d(TAG, "Drag start: ${element.id}")
                                onInteractionStart()
                            },
                            onDragEnd = {
                                Log.d(TAG, "Drag end: ${element.id}")
                                onMoveEnd()
                                onInteractionEnd()
                            },
                            onDragCancel = {
                                onMoveEnd()
                                onInteractionEnd()
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            onMoveBy(dragAmount)
                        }
                    }
                } else if (isInteractive) {
                    // Tap to select
                    Modifier.pointerInput(element.id) {
                        detectTapGestures {
                            onSelect()
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color(0xFF2196F3))
                } else {
                    Modifier
                }
            )
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Inserted image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }

    // ─── Resize handles — zIndex 20 (ABOVE image) ───
    if (isSelected && isInteractive) {
        Box(
            modifier = Modifier
                .zIndex(20f)
                .offset { IntOffset(element.position.x.toInt(), element.position.y.toInt()) }
                .size(
                    width = with(density) { scaledWidth.toDp() },
                    height = with(density) { scaledHeight.toDp() }
                )
                .graphicsLayer {
                    rotationZ = element.rotation
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
        ) {
            ResizeHandles(
                elementWidth = scaledWidth,
                elementHeight = scaledHeight,
                onResizeByHandle = onResizeByHandle,
                onResizeEnd = onResizeEnd,
                onInteractionStart = onInteractionStart,
                onInteractionEnd = onInteractionEnd
            )
        }
    }
}
