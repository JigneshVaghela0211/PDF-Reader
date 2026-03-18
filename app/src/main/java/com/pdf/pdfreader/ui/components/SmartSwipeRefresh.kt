package com.pdf.pdfreader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun SmartSwipeRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    headerColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val refreshThreshold = with(density) { 80.dp.toPx() }
    val maxDragDist = with(density) { 200.dp.toPx() }
    
    val pullDistance = remember { Animatable(0f) }
    var isPulling by remember { mutableStateOf(false) }
    
    // Using a more robust connection that handles all scroll phases
    val nestedScrollConnection = remember(isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Return consumed offset to prevent the list from scrolling up while retracting
                return if (available.y < 0 && pullDistance.value > 0) {
                    val newOffset = (pullDistance.value + available.y).coerceAtLeast(0f)
                    scope.launch { pullDistance.snapTo(newOffset) }
                    Offset(0f, available.y)
                } else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    isPulling = true
                    // Damping calculation
                    val dragPercent = (pullDistance.value / maxDragDist).coerceIn(0f, 1f)
                    val damping = 1f - dragPercent.pow(2)
                    val delta = available.y * damping * 0.5f
                    
                    scope.launch { 
                        pullDistance.snapTo((pullDistance.value + delta).coerceAtMost(maxDragDist)) 
                    }
                    Offset(0f, available.y)
                } else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                isPulling = false
                if (pullDistance.value >= refreshThreshold && !isRefreshing) {
                    onRefresh()
                }
                
                // Final snap back
                val target = if (isRefreshing) refreshThreshold else 0f
                pullDistance.animateTo(
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                )
                return Velocity.Zero
            }
        }
    }

    // Secondary state sync to ensure closure when viewmodel completes
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pullDistance.value > 0 && !isPulling) {
            pullDistance.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        } else if (isRefreshing && pullDistance.value < refreshThreshold) {
            pullDistance.animateTo(refreshThreshold, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(nestedScrollConnection)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val pullPercent = (pullDistance.value / refreshThreshold).coerceAtLeast(0f)
        
        // --- THE HEADER: Only visible when and for the amount pulled ---
        if (pullDistance.value > 0.5f) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { (pullDistance.value * 1.5f).toDp() })
            ) {
                val width = size.width
                val height = pullDistance.value
                
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width, 0f)
                    lineTo(width, height * 0.85f)
                    quadraticTo(width / 2, height * (1f + (pullPercent * 0.1f).coerceAtMost(0.3f)), 0f, height * 0.85f)
                    close()
                }
                
                drawPath(path = path, color = headerColor)
            }
            
            // Header Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { refreshThreshold.toDp() })
                    .offset { IntOffset(0, (pullDistance.value - refreshThreshold).roundToInt() / 2) }
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

        // --- THE CONTENT: Displaced purely by pullDistance ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pullDistance.value.roundToInt()) }
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
