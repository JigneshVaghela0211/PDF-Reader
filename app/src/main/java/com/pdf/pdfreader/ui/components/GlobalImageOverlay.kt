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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.domain.model.ResizeHandle

private const val TAG = "GlobalImageOverlay"

/** Compose layout max constraint = 262143. Clamp to stay safely under. */
private const val MAX_SIZE_PX = 262000f

/**
 * Data class representing a page's position in the global (scrollable) coordinate space.
 * Computed from LazyListState.layoutInfo.visibleItemsInfo.
 */
data class PageLayout(
    val pageIndex: Int,
    /** The Y offset of this page's top edge relative to the viewport top, in pixels */
    val offsetInViewport: Int,
    /** The height of this page in pixels */
    val height: Int
)

/**
 * Computes the list of currently visible page layouts from the scroll state.
 * Each PageLayout contains the page index, its Y offset in the viewport, and its height.
 */
@Composable
fun rememberVisiblePageLayouts(scrollState: LazyListState): List<PageLayout> {
    return remember(scrollState.layoutInfo) {
        scrollState.layoutInfo.visibleItemsInfo.map { item ->
            PageLayout(
                pageIndex = item.index,
                offsetInViewport = item.offset,
                height = item.size
            )
        }
    }
}

/**
 * GLOBAL image overlay that renders ALL image elements in a single layer
 * ABOVE the LazyColumn. This is the architectural fix for images going
 * behind subsequent pages.
 *
 * Architecture:
 *   - Lives OUTSIDE individual PdfPage composables
 *   - Positioned at zIndex(100f) above the page list
 *   - Uses scrollState to compute global Y position for each image
 *   - Images on non-visible pages are skipped (performance)
 *   - Cross-page dragging works because images are NOT clipped by page containers
 *
 * Position computation:
 *   globalY = pageTopInViewport + element.position.y
 *   globalX = element.position.x  (unchanged — pages are full-width)
 */
@Composable
fun GlobalImageOverlay(
    modifier: Modifier = Modifier,
    scrollState: LazyListState,
    imageElements: List<ImageElement>,
    selectedImageId: String?,
    selectedImageIds: Set<String> = emptySet(),
    isImageMode: Boolean,
    onSelectImage: (String?) -> Unit,
    onMoveImage: (String, Offset) -> Unit,
    onResizeImage: (String, ResizeHandle, Offset) -> Unit,
    onResizeGroup: (Set<String>, ResizeHandle, Offset, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onResizeEnd: (String) -> Unit,
    onResizeGroupEnd: (Set<String>) -> Unit = { _ -> },
    onMoveEnd: (String) -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    onPageChanged: (String, Int, Offset) -> Unit = { _, _, _ -> }
) {
    if (imageElements.isEmpty()) return

    val pageLayouts = rememberVisiblePageLayouts(scrollState)
    if (pageLayouts.isEmpty()) return

    // Build a lookup: pageIndex -> PageLayout for visible pages
    val visiblePages = remember(pageLayouts) {
        pageLayouts.associateBy { it.pageIndex }
    }

    // Expand visibility to include ±1 page buffer for smooth transitions
    val visiblePageRange = remember(pageLayouts) {
        if (pageLayouts.isEmpty()) IntRange.EMPTY
        else {
            val minPage = (pageLayouts.first().pageIndex - 1).coerceAtLeast(0)
            val maxPage = pageLayouts.last().pageIndex + 1
            minPage..maxPage
        }
    }

    // Filter images to those on or near visible pages
    val visibleImages = remember(imageElements, visiblePageRange) {
        imageElements
            .filter { it.pageIndex in visiblePageRange }
            .sortedBy { it.zIndex }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f) // ALWAYS above PDF pages
    ) {
        // ─── Deselect tap catcher ───
        if (isImageMode && selectedImageId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(selectedImageId) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                Log.d(TAG, "Tap on empty space → deselect")
                                onSelectImage(null)
                            }
                        }
                    }
            )
        }

        // ─── Group Computations ───
        val activeGroupItems = remember(visibleImages, selectedImageId, selectedImageIds) {
            if (selectedImageIds.size > 1) {
                visibleImages.filter { selectedImageIds.contains(it.id) }
            } else if (selectedImageId != null) {
                val primary = visibleImages.find { it.id == selectedImageId }
                if (primary?.groupId != null) {
                    visibleImages.filter { it.groupId == primary.groupId }
                } else emptyList()
            } else emptyList()
        }
        val activeGroupIds = remember(activeGroupItems) { activeGroupItems.map { it.id }.toSet() }

        // ─── Render each visible image at its global position ───
        visibleImages.forEach { element ->
            val pageLayout = visiblePages[element.pageIndex]
            // If this page isn't currently visible, skip rendering
                ?: return@forEach

            // Compute global position in the viewport
            val globalX = element.position.x
            val globalY = pageLayout.offsetInViewport.toFloat() + element.position.y

            key(element.id) {
                GlobalImageElementView(
                    element = element,
                    globalX = globalX,
                    globalY = globalY,
                    isSelected = element.id == selectedImageId || selectedImageIds.contains(element.id),
                    showGroupHandles = !activeGroupIds.contains(element.id), // hide individual handles if this element is part of active group
                    isInteractive = isImageMode,
                    pageLayouts = pageLayouts,
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
                    onPageChanged = { newPageIndex, newRelativePos ->
                        onPageChanged(element.id, newPageIndex, newRelativePos)
                    }
                )
            }
        }
        
        // ─── Render Group Bounding Box ───
        if (activeGroupItems.size > 1) {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            
            activeGroupItems.forEach { item ->
                val layout = visiblePages[item.pageIndex] ?: return@forEach
                val gX = item.position.x
                val gY = layout.offsetInViewport.toFloat() + item.position.y
                val w = item.width * item.scale
                val h = item.height * item.scale
                
                if (gX < minX) minX = gX
                if (gY < minY) minY = gY
                if (gX + w > maxX) maxX = gX + w
                if (gY + h > maxY) maxY = gY + h
            }
            
            if (minX <= maxX) {
                val groupW = maxX - minX
                val groupH = maxY - minY
                val density = LocalDensity.current
                
                Box(
                    modifier = Modifier
                        .zIndex(150f)
                        .offset { IntOffset(minX.toInt(), minY.toInt()) }
                        .size(
                            width = with(density) { groupW.toDp() },
                            height = with(density) { groupH.toDp() }
                        )
                ) {
                    // Draw Group bounds
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(0xFFFF9800),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                    
                    if (isImageMode) {
                        ResizeHandles(
                            elementWidth = groupW,
                            elementHeight = groupH,
                            onResizeByHandle = { handle, delta ->
                                onResizeGroup(activeGroupIds, handle, delta, groupW, groupH)
                            },
                            onResizeEnd = { 
                                onResizeGroupEnd(activeGroupIds)
                            },
                            onInteractionStart = onInteractionStart,
                            onInteractionEnd = onInteractionEnd
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual image element rendered at a global (viewport) position.
 * Handles drag with cross-page boundary detection.
 */
@Composable
private fun GlobalImageElementView(
    element: ImageElement,
    globalX: Float,
    globalY: Float,
    isSelected: Boolean,
    showGroupHandles: Boolean,
    isInteractive: Boolean,
    pageLayouts: List<PageLayout>,
    onSelect: () -> Unit,
    onMoveBy: (Offset) -> Unit,
    onResizeByHandle: (ResizeHandle, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onMoveEnd: () -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    onPageChanged: (Int, Offset) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Load bitmap from URI (cached, busted by bitmapVersion for re-rendered signatures)
    val bitmapKey = "${element.uri}_v${element.bitmapVersion}"
    val bitmap = remember(bitmapKey) {
        try {
            val uri = Uri.parse(element.uri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply {
                // Signatures are small PNGs — don't downsample them
                inSampleSize = if (element.isSignature) 1 else 2
            }
            inputStream?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: ${element.uri}", e)
            null
        }
    }

    DisposableEffect(bitmap) {
        onDispose {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    if (bitmap == null || bitmap.isRecycled) return

    val scaledWidth = (element.width * element.scale).coerceIn(1f, MAX_SIZE_PX)
    val scaledHeight = (element.height * element.scale).coerceIn(1f, MAX_SIZE_PX)
    val isLocked = element.isLocked

    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 112f + element.zIndex else 110f + element.zIndex)
            .offset {
                IntOffset(
                    globalX.toInt(),
                    globalY.toInt()
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
                    if (isSelected) {
                        Modifier.border(2.dp, Color(0xFFFF9800))
                    } else {
                        Modifier
                    }
                } else if (isInteractive && isSelected) {
                    // DRAG gesture with cross-page detection
                    Modifier.pointerInput(element.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onInteractionStart()

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
                    // Tap to select
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
        // Image content
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Inserted image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Resize handles (above image, gesture priority over parent drag)
        if (isSelected && isInteractive && !isLocked && showGroupHandles) {
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
