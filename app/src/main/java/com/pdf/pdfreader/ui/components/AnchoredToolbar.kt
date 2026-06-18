package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Positions a floating contextual toolbar relative to a selected element so that it:
 *  - sits ABOVE the element when there's room, otherwise flips BELOW it,
 *  - keeps clear of the element's resize handles (which extend ~22dp past the edge),
 *  - stays fully on-screen (clamped horizontally and vertically),
 *  - is horizontally centred on the element.
 *
 * The [content] receives the maximum width available for the toolbar so it can wrap
 * its options (e.g. via FlowRow) and keep every action reachable on any screen size.
 *
 * Anchor values are in viewport pixels.
 */
@Composable
fun AnchoredToolbar(
    visible: Boolean,
    anchorCenterX: Int,
    anchorTop: Int,
    anchorBottom: Int,
    content: @Composable (maxWidth: Dp) -> Unit
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val margin = with(density) { 8.dp.toPx() }.toInt()
        // Clearance to clear the 44dp resize handles (centred on the edge → ~22dp out).
        val gap = with(density) { 30.dp.toPx() }.toInt()
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val availableWidthDp = with(density) { (maxW - margin * 2).coerceAtLeast(0).toDp() }

        val x = if (size.width > 0)
            (anchorCenterX - size.width / 2).coerceIn(margin, (maxW - size.width - margin).coerceAtLeast(margin))
        else margin

        val yAbove = anchorTop - size.height - gap
        val yBelow = anchorBottom + gap
        val y = when {
            size.height == 0 -> anchorTop.coerceAtLeast(margin)
            yAbove >= margin -> yAbove
            yBelow + size.height + margin <= maxH -> yBelow
            else -> (maxH - size.height - margin).coerceAtLeast(margin)
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.offset { IntOffset(x, y) },
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Box(modifier = Modifier.onSizeChanged { size = it }) {
                content(availableWidthDp)
            }
        }
    }
}
