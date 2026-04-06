package com.pdf.pdfreader.ui.components

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
import com.pdf.pdfreader.domain.model.ResizeHandle

/**
 * Draws 8 resize handles around a selected image element.
 *
 * Handles are positioned at:
 * - 4 corners: top-left, top-right, bottom-left, bottom-right
 * - 4 edge midpoints: top-center, bottom-center, left-center, right-center
 *
 * Each handle is a draggable circle that reports drag deltas back to the parent.
 */
@Composable
fun ResizeHandles(
    elementWidth: Float,
    elementHeight: Float,
    onResizeByHandle: (ResizeHandle, Offset) -> Unit,
    onResizeEnd: () -> Unit
) {
    val handleRadius = 6.dp
    val handleRadiusPx = with(LocalDensity.current) { handleRadius.toPx() }
    val hitAreaSize = 24.dp

    // Define handle positions relative to the element (0,0 = top-left)
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

    // Draw selection border
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Dashed selection border
                drawRect(
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 2f)
                )
            }
    )

    // Draw individual handles
    handles.forEach { (handle, position) ->
        val hitAreaSizePx = with(LocalDensity.current) { hitAreaSize.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (position.x - hitAreaSizePx / 2).toInt(),
                        (position.y - hitAreaSizePx / 2).toInt()
                    )
                }
                .size(hitAreaSize)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // White fill
                    drawCircle(
                        color = Color.White,
                        radius = handleRadiusPx,
                        center = center,
                        style = Fill
                    )
                    // Blue border
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = handleRadiusPx,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
                .pointerInput(handle) {
                    detectDragGestures(
                        onDragEnd = { onResizeEnd() }
                    ) { change, dragAmount ->
                        change.consume()
                        onResizeByHandle(handle, dragAmount)
                    }
                }
        )
    }
}
