package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Enhanced floating toolbar displayed when an image element is selected.
 * Provides: Rotate, Delete, Duplicate, Layer control, Lock, Opacity, Center.
 */
@Composable
fun ImageEditToolbar(
    visible: Boolean,
    offsetX: Int,
    offsetY: Int,
    isLocked: Boolean = false,
    opacity: Float = 1f,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    onBringToFront: () -> Unit = {},
    onSendToBack: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onOpacityChange: (Float) -> Unit = {},
    onSnapToCenter: () -> Unit = {}
) {
    var showOpacitySlider by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.coerceAtLeast(0), offsetY) }
        ) {
            // Main toolbar row
            Row(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(24.dp))
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Rotate Left
                IconButton(
                    onClick = onRotateLeft,
                    modifier = Modifier.size(34.dp),
                    enabled = !isLocked
                ) {
                    Icon(
                        Icons.Default.RotateLeft,
                        contentDescription = "Rotate Left",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Rotate Right
                IconButton(
                    onClick = onRotateRight,
                    modifier = Modifier.size(34.dp),
                    enabled = !isLocked
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = "Rotate Right",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                ToolbarDivider()

                // Duplicate
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center
                IconButton(
                    onClick = onSnapToCenter,
                    modifier = Modifier.size(34.dp),
                    enabled = !isLocked
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Center",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                ToolbarDivider()

                // Bring to Front
                IconButton(
                    onClick = onBringToFront,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.FlipToFront,
                        contentDescription = "Bring to Front",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Send to Back
                IconButton(
                    onClick = onSendToBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.FlipToBack,
                        contentDescription = "Send to Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                ToolbarDivider()

                // Lock/Unlock
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = if (isLocked) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Opacity toggle
                IconButton(
                    onClick = { showOpacitySlider = !showOpacitySlider },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Opacity,
                        contentDescription = "Opacity",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                ToolbarDivider()

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Image",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Opacity slider (expandable)
            AnimatedVisibility(visible = showOpacitySlider) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${(opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    )
}
