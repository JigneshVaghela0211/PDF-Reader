package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSwipeRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    headerColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val state = rememberPullToRefreshState()

    val refreshThresholdDp = 80.dp
    val refreshThreshold = with(density) { refreshThresholdDp.toPx() }

    // The Material3 modifier owns the gesture: it engages reliably from the very
    // top, applies natural drag resistance, and animates the release/settle and the
    // "hold while refreshing" states for us. We only read distanceFraction to drive
    // the custom header (1f == threshold reached, >1f == pulled past it).
    val pullDistance = state.distanceFraction * refreshThreshold
    val pullPercent = state.distanceFraction.coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = state,
                threshold = refreshThresholdDp,
                onRefresh = onRefresh
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- THE HEADER: only visible for the amount pulled ---
        if (pullDistance > 0.5f) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { (pullDistance * 1.5f).toDp() })
            ) {
                val width = size.width
                val height = pullDistance

                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width, 0f)
                    lineTo(width, height * 0.85f)
                    quadraticTo(width / 2, height * (1f + (pullPercent * 0.1f).coerceAtMost(0.3f)), 0f, height * 0.85f)
                    close()
                }

                drawPath(path = path, color = headerColor)
            }

            // Header content box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { refreshThreshold.toDp() })
                    .offset { IntOffset(0, (pullDistance - refreshThreshold).roundToInt() / 2) }
                    .graphicsLayer { alpha = pullPercent.coerceAtMost(1f) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartLoadingSpinner(
                        color = Color.White,
                        isRefreshing = isRefreshing,
                        progress = pullPercent
                    )
                    Text(
                        text = when {
                            isRefreshing -> "Updating..."
                            pullPercent > 1f -> "Release to Refresh"
                            else -> "Pull Down"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // --- THE CONTENT: displaced by the pull amount. Reading distanceFraction
        // inside the offset lambda keeps the displacement in the layout phase, so the
        // content subtree is not recomposed on every frame of the drag. ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (state.distanceFraction * refreshThreshold).roundToInt()) }
        ) {
            content()
        }
    }
}

@Composable
fun SmartLoadingSpinner(
    color: Color,
    isRefreshing: Boolean,
    progress: Float
) {
    if (isRefreshing) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = color,
            strokeWidth = 2.5.dp,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    } else {
        val rotation = progress * 360f
        Canvas(modifier = Modifier.size(20.dp)) {
            drawArc(
                color = color,
                startAngle = rotation,
                sweepAngle = (progress * 240f).coerceAtMost(240f),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}
