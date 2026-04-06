package com.pdf.pdfreader.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.domain.model.ResizeHandle

/**
 * Overlay composable that renders all inserted images on a PDF page.
 * Handles:
 * - Image rendering with position, scale, and rotation transforms
 * - Drag-to-move when selected
 * - Resize via 8-handle system
 * - Selection on tap
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
    onMoveEnd: (String) -> Unit
) {
    if (pageSize == IntSize.Zero) return

    val context = LocalContext.current
    val pageImages = imageElements.filter { it.pageIndex == pageIndex }

    Box(modifier = modifier.fillMaxSize()) {
        pageImages.forEach { element ->
            key(element.id) {
                ImageElementView(
                    element = element,
                    isSelected = element.id == selectedImageId,
                    isInteractive = isImageMode,
                    onSelect = { onSelectImage(element.id) },
                    onMoveBy = { delta -> onMoveImage(element.id, delta) },
                    onResizeByHandle = { handle, delta -> onResizeImage(element.id, handle, delta) },
                    onResizeEnd = { onResizeEnd(element.id) },
                    onMoveEnd = { onMoveEnd(element.id) }
                )
            }
        }

        // Tap on empty space to deselect
        if (isImageMode && selectedImageId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedImageId) {
                        detectTapGestures { offset ->
                            val isOnImage = pageImages.any { elem ->
                                val scaledW = elem.width * elem.scale
                                val scaledH = elem.height * elem.scale
                                offset.x >= elem.position.x &&
                                        offset.x <= elem.position.x + scaledW &&
                                        offset.y >= elem.position.y &&
                                        offset.y <= elem.position.y + scaledH
                            }
                            if (!isOnImage) {
                                onSelectImage(null)
                            }
                        }
                    }
            )
        }
    }
}

/**
 * Individual image element with drag, resize handles, and rotation.
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
    onMoveEnd: () -> Unit
) {
    val context = LocalContext.current

    // Load bitmap from URI (cached)
    val bitmap = remember(element.uri) {
        try {
            val uri = Uri.parse(element.uri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2 // Downsample for memory safety
            }
            inputStream?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap == null || bitmap.isRecycled) return

    val scaledWidth = element.width * element.scale
    val scaledHeight = element.height * element.scale

    Box(
        modifier = Modifier
            .offset { IntOffset(element.position.x.toInt(), element.position.y.toInt()) }
            .size(
                width = with(androidx.compose.ui.platform.LocalDensity.current) { scaledWidth.toDp() },
                height = with(androidx.compose.ui.platform.LocalDensity.current) { scaledHeight.toDp() }
            )
            .graphicsLayer {
                rotationZ = element.rotation
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
            .then(
                if (isInteractive) {
                    Modifier
                        .pointerInput(element.id) {
                            detectTapGestures { onSelect() }
                        }
                        .pointerInput(element.id, isSelected) {
                            if (isSelected) {
                                detectDragGestures(
                                    onDragEnd = { onMoveEnd() }
                                ) { change, dragAmount ->
                                    change.consume()
                                    onMoveBy(dragAmount)
                                }
                            }
                        }
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

        // Draw resize handles when selected
        if (isSelected && isInteractive) {
            ResizeHandles(
                elementWidth = scaledWidth,
                elementHeight = scaledHeight,
                onResizeByHandle = onResizeByHandle,
                onResizeEnd = onResizeEnd
            )
        }
    }
}
