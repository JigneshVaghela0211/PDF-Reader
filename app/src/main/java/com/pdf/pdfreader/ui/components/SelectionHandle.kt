package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A draggable text-selection handle (Adobe/Xodo style), centered at [centerX]/[centerY] given in
 * page pixels. While dragging it reports the absolute touch position in page-pixel space via
 * [onDrag], so the caller can hit-test which word the handle is currently over.
 *
 * The center is read live each frame, so the handle follows the selection as it grows/shrinks, but
 * the drag accumulates from the position captured when the drag started to avoid feedback jitter.
 */
@Composable
fun SelectionHandle(
    centerX: Float,
    centerY: Float,
    color: Color,
    onDrag: (pageOffset: Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val touchRadiusPx = with(density) { 18.dp.toPx() }
    val dotRadiusPx = with(density) { 6.dp.toPx() }

    val latestX = rememberUpdatedState(centerX)
    val latestY = rememberUpdatedState(centerY)

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (latestX.value - touchRadiusPx).roundToInt(),
                    (latestY.value - touchRadiusPx).roundToInt()
                )
            }
            .size(with(density) { (touchRadiusPx * 2).toDp() })
            .pointerInput(Unit) {
                var base = Offset.Zero
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        base = Offset(latestX.value, latestY.value)
                        acc = Offset.Zero
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        acc += delta
                        onDrag(base + acc)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = color, radius = dotRadiusPx, center = Offset(size.width / 2f, size.height / 2f))
        }
    }
}
