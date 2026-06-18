package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A vertical, draggable page scrollbar pinned to the right edge of the reader.
 *
 * - The thumb position reflects the current page.
 * - Dragging the thumb jumps to the matching page and shows a "page / total"
 *   bubble next to it.
 * - Auto-shows while scrolling/dragging and fades out otherwise (driven by the
 *   `visible` flag, plus it stays visible during an active drag).
 */
@Composable
fun PageScrollbar(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) return

    val density = LocalDensity.current
    val thumbHeight = 56.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val lastIndex = (totalPages - 1).coerceAtLeast(1)

    var isDragging by remember { mutableStateOf(false) }
    // Current thumb top offset within the track, in pixels.
    var thumbOffsetPx by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = visible || isDragging,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .width(72.dp)
        ) {
            // Track height comes straight from the layout constraints (px) — no
            // state written during composition, so there's no recomposition loop.
            val trackHeightPx = constraints.maxHeight.toFloat()
            val maxOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

            // Keep the thumb synced to the current page while not dragging.
            LaunchedEffect(currentPage, maxOffset, isDragging) {
                if (!isDragging) {
                    val fraction = currentPage.toFloat() / lastIndex
                    thumbOffsetPx = (fraction * maxOffset).coerceIn(0f, maxOffset)
                }
            }

            // Page bubble shown while dragging, vertically centered on the thumb.
            if (isDragging) {
                val bubbleCenter = thumbOffsetPx + thumbHeightPx / 2f
                val bubbleHalfPx = with(density) { 16.dp.toPx() }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, (bubbleCenter - bubbleHalfPx).roundToInt()) }
                ) {
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // The draggable thumb, pinned to the right edge.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .size(width = 10.dp, height = thumbHeight)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    )
                    .pointerInput(totalPages, maxOffset) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            thumbOffsetPx = (thumbOffsetPx + dragAmount).coerceIn(0f, maxOffset)
                            val fraction = thumbOffsetPx / maxOffset
                            val page = (fraction * lastIndex).roundToInt().coerceIn(0, lastIndex)
                            onPageChange(page)
                        }
                    }
            )
        }
    }
}
