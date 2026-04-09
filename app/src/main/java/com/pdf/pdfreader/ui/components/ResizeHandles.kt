package com.pdf.pdfreader.ui.components

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.ResizeHandle

private const val TAG = "ResizeHandles"

/**
 * Draws 8 resize handles around a selected image element.
 *
 * Each handle:
 * - Has a large 44dp touch target (only 7dp visible circle)
 * - Uses awaitEachGesture to consume pointer-down IMMEDIATELY
 *   — this prevents parent scroll from stealing the gesture
 * - Is at zIndex(10f) to stay above the image body
 * - Signals InteractionMode.RESIZE on initial touch
 *
 * FIX: Uses incremental deltas (current - previous) instead of
 * cumulative (current - initial). This prevents the feedback loop where
 * the coordinate space shifts as the element resizes.
 */
@Composable
fun ResizeHandles(
    elementWidth: Float,
    elementHeight: Float,
    onResizeByHandle: (ResizeHandle, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {}
) {
    val handleVisualRadius = 7.dp
    val handleVisualRadiusPx = with(LocalDensity.current) { handleVisualRadius.toPx() }
    // Active handle visual radius (larger when dragging)
    val activeHandleVisualRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }
    // 44dp — Android accessibility minimum touch target
    val hitAreaSize = 44.dp

    // Track which handle is currently being dragged for visual feedback
    var activeHandle by remember { mutableStateOf<ResizeHandle?>(null) }

    val handles = listOf(
        ResizeHandle.TOP_LEFT to Offset(0f, 0f),
        ResizeHandle.TOP_CENTER to Offset(elementWidth / 2f, 0f),
        ResizeHandle.TOP_RIGHT to Offset(elementWidth, 0f),
        ResizeHandle.LEFT_CENTER to Offset(0f, elementHeight / 2f),
        ResizeHandle.RIGHT_CENTER to Offset(elementWidth, elementHeight / 2f),
        ResizeHandle.BOTTOM_LEFT to Offset(0f, elementHeight),
        ResizeHandle.BOTTOM_CENTER to Offset(elementWidth / 2f, elementHeight),
        ResizeHandle.BOTTOM_RIGHT to Offset(elementWidth, elementHeight)
    )

    // Selection border
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 2f)
                )
            }
    )

    // Individual resize handles
    handles.forEach { (handle, position) ->
        val hitAreaSizePx = with(LocalDensity.current) { hitAreaSize.toPx() }
        val isActive = activeHandle == handle

        Box(
            modifier = Modifier
                .zIndex(10f) // Above image body and selection border
                .offset {
                    IntOffset(
                        (position.x - hitAreaSizePx / 2).toInt(),
                        (position.y - hitAreaSizePx / 2).toInt()
                    )
                }
                .size(hitAreaSize)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = if (isActive) activeHandleVisualRadiusPx else handleVisualRadiusPx
                    val borderColor = if (isActive) Color(0xFF1565C0) else Color(0xFF2196F3)
                    val fillColor = if (isActive) Color(0xFFBBDEFB) else Color.White

                    // Fill circle
                    drawCircle(
                        color = fillColor,
                        radius = radius,
                        center = center,
                        style = Fill
                    )
                    // Border circle
                    drawCircle(
                        color = borderColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = if (isActive) 3f else 2.5f)
                    )
                }
                // CRITICAL FIX: Use incremental deltas instead of cumulative.
                // Track previousPosition and compute (current - previous) each frame.
                // This eliminates the feedback loop where coordinate space shifts
                // during resize caused jumpy/inaccurate behavior.
                .pointerInput(handle) {
                    awaitEachGesture {
                        // 1. Await initial touch-down — consume it immediately
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        Log.d(TAG, "Handle DOWN: $handle")
                        activeHandle = handle
                        onInteractionStart()

                        // 2. Track drag movement using INCREMENTAL deltas
                        var previousPosition = down.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break

                                if (change.pressed) {
                                    // INCREMENTAL delta: current position minus previous position
                                    val incrementalDelta = change.position - previousPosition
                                    change.consume()

                                    if (incrementalDelta != Offset.Zero) {
                                        onResizeByHandle(handle, incrementalDelta)
                                    }
                                    // Update previous position for next frame
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

                        Log.d(TAG, "Handle UP: $handle")
                        activeHandle = null
                        onResizeEnd()
                        onInteractionEnd()
                    }
                }
        )
    }
}
