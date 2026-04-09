package com.pdf.pdfreader.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
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

/** Compose layout max constraint = 262143. Clamp to stay safely under. */
private const val MAX_SIZE_PX = 262000f

/**
 * Overlay composable that renders all inserted images on a PDF page.
 *
 * Architecture (critical for gesture priority):
 *   - Each image is a SINGLE Box containing both the image and its resize handles.
 *   - Handles are children of the image Box, so their pointerInput handlers
 *     run BEFORE the parent's drag handler (depth-first event dispatch).
 *   - Deselect tap catcher sits below all images at zIndex(0).
 *
 * ENHANCEMENTS:
 *   - Images sorted by zIndex for correct layer ordering
 *   - Locked images have disabled gestures
 *   - Opacity applied via graphicsLayer
 *   - Drag uses incremental deltas (same fix as resize)
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
    onInteractionEnd: () -> Unit = {},
    pageWidth: Int = 0,
    pageHeight: Int = 0
) {
    if (pageSize == IntSize.Zero) return

    // Sort by zIndex so higher values render on top
    val pageImages = remember(imageElements, pageIndex) {
        imageElements
            .filter { it.pageIndex == pageIndex }
            .sortedBy { it.zIndex }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Deselect tap catcher BELOW images (zIndex = 0) ───
        if (isImageMode && selectedImageId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(selectedImageId) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Wait for up to determine if it's a tap (not a drag)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                Log.d(TAG, "Tap on empty space → deselect")
                                onSelectImage(null)
                            }
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
                        onMoveImage(element.id, delta)
                    },
                    onResizeByHandle = { handle, delta ->
                        onResizeImage(element.id, handle, delta)
                    },
                    onResizeEnd = { onResizeEnd(element.id) },
                    onMoveEnd = { onMoveEnd(element.id) },
                    onInteractionStart = onInteractionStart,
                    onInteractionEnd = onInteractionEnd,
                    pageWidth = pageSize.width,
                    pageHeight = pageSize.height
                )
            }
        }
    }
}

/**
 * Individual image element with unified gesture hierarchy.
 *
 * FIX: Drag now uses incremental deltas (current - previous) instead of
 * cumulative (current - initial), matching the resize handle fix.
 *
 * Lock support: When element.isLocked, no gesture handlers are attached.
 * Opacity: Applied via graphicsLayer alpha.
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
    onInteractionEnd: () -> Unit,
    pageWidth: Int,
    pageHeight: Int
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

    val scaledWidth = (element.width * element.scale).coerceIn(1f, MAX_SIZE_PX)
    val scaledHeight = (element.height * element.scale).coerceIn(1f, MAX_SIZE_PX)

    // Determine if element is locked — no gestures when locked
    val isLocked = element.isLocked

    // ─── UNIFIED Box: image body + handles as children ───
    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 12f + element.zIndex else 10f + element.zIndex)
            .offset {
                IntOffset(
                    element.position.x.toInt().coerceAtLeast(0),
                    element.position.y.toInt().coerceAtLeast(0)
                )
            }
            .size(
                width = with(density) { scaledWidth.toDp() },
                height = with(density) { scaledHeight.toDp() }
            )
            .graphicsLayer {
                rotationZ = element.rotation
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                alpha = element.opacity
            }
            .then(
                if (isLocked) {
                    // Locked: show lock border but no gesture handling
                    if (isSelected) {
                        Modifier.border(2.dp, Color(0xFFFF9800)) // Orange border for locked+selected
                    } else {
                        Modifier
                    }
                } else if (isInteractive && isSelected) {
                    // DRAG gesture — uses INCREMENTAL deltas (FIX)
                    Modifier.pointerInput(element.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onInteractionStart()

                            // INCREMENTAL delta tracking
                            var previousPosition = down.position
                            var hasDragged = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break

                                    if (change.pressed) {
                                        val incrementalDelta = change.position - previousPosition
                                        change.consume()

                                        if (incrementalDelta != Offset.Zero) {
                                            hasDragged = true
                                            onMoveBy(incrementalDelta)
                                        }
                                        previousPosition = change.position
                                    } else {
                                        // Pointer released
                                        change.consume()
                                        break
                                    }
                                }
                            } catch (_: Exception) {
                                // Gesture cancelled
                            }

                            if (hasDragged) {
                                onMoveEnd()
                            }
                            onInteractionEnd()
                        }
                    }
                } else if (isInteractive) {
                    // Tap to select when NOT already selected
                    Modifier.pointerInput(element.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                onSelect()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (isSelected && !isLocked) {
                    Modifier.border(2.dp, Color(0xFF2196F3))
                } else {
                    Modifier
                }
            )
    ) {
        // ─── Child 1: Image content ───
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Inserted image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // ─── Child 2: Resize handles (ABOVE image, gesture priority over parent drag) ───
        if (isSelected && isInteractive && !isLocked) {
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
