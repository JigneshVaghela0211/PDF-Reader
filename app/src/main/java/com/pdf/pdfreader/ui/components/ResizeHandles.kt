package com.pdf.pdfreader.ui.components

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * - Has a large 32dp touch target (only 6dp visible circle)
 * - Uses its own pointerInput to avoid gesture conflicts
 * - Is at zIndex(10f) to stay above the image body
 * - Fully consumes drag events to prevent parent scroll
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
    // Large touch target — easy to grab
    val hitAreaSize = 32.dp

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

                    // White fill circle
                    drawCircle(
                        color = Color.White,
                        radius = handleVisualRadiusPx,
                        center = center,
                        style = Fill
                    )
                    // Blue border circle
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = handleVisualRadiusPx,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
                .pointerInput(handle) {
                    detectDragGestures(
                        onDragStart = {
                            Log.d(TAG, "Handle drag start: $handle")
                            onInteractionStart()
                        },
                        onDragEnd = {
                            Log.d(TAG, "Handle drag end: $handle")
                            onResizeEnd()
                            onInteractionEnd()
                        },
                        onDragCancel = {
                            onResizeEnd()
                            onInteractionEnd()
                        }
                    ) { change, dragAmount ->
                        change.consume() // CRITICAL: prevents parent scroll
                        onResizeByHandle(handle, dragAmount)
                    }
                }
        )
    }
}
