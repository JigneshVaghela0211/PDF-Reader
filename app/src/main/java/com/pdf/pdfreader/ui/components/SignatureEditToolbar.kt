package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Context-aware editing toolbar shown when a SIGNATURE element is selected.
 * Provides signature-specific controls (thickness, color) plus standard image tools.
 */
@Composable
fun SignatureEditToolbar(
    visible: Boolean,
    offsetX: Int,
    offsetY: Int,
    isLocked: Boolean = false,
    opacity: Float = 1f,
    currentStrokeWidth: Float = 5f,
    currentColor: Color = Color.Black,
    hasEditableStrokes: Boolean = false,
    onStrokeWidthChange: (Float) -> Unit = {},
    onColorChange: (Color) -> Unit = {},
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    onBringToFront: () -> Unit = {},
    onSendToBack: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onOpacityChange: (Float) -> Unit = {}
) {
    var showThicknessPanel by remember { mutableStateOf(false) }
    var showColorPanel by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    val signatureColors = listOf(
        Color.Black,
        Color(0xFF1565C0), // Blue
        Color(0xFFD32F2F), // Red
        Color(0xFF2E7D32), // Green
        Color(0xFF6A1B9A), // Purple
        Color(0xFFEF6C00)  // Orange
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        Column(
            modifier = Modifier.offset { IntOffset(offsetX.coerceAtLeast(0), offsetY) }
        ) {
            // ─── Main toolbar row ───
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
                // ─── Signature-specific: Thickness ───
                if (hasEditableStrokes) {
                    IconButton(
                        onClick = { 
                            showThicknessPanel = !showThicknessPanel
                            showColorPanel = false
                            showOpacitySlider = false
                        },
                        modifier = Modifier.size(34.dp),
                        enabled = !isLocked
                    ) {
                        // Thickness icon — draw a circle indicating pen size
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawCircle(
                                color = currentColor,
                                radius = (currentStrokeWidth / 20f * 8f).coerceIn(2f, 8f)
                            )
                        }
                    }

                    // ─── Signature-specific: Color ───
                    IconButton(
                        onClick = { 
                            showColorPanel = !showColorPanel
                            showThicknessPanel = false
                            showOpacitySlider = false
                        },
                        modifier = Modifier.size(34.dp),
                        enabled = !isLocked
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }

                    SignatureToolbarDivider()
                }

                // ─── Standard image tools ───
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

                SignatureToolbarDivider()

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

                // Layer controls
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

                SignatureToolbarDivider()

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
                    onClick = { 
                        showOpacitySlider = !showOpacitySlider
                        showThicknessPanel = false
                        showColorPanel = false
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Opacity,
                        contentDescription = "Opacity",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                SignatureToolbarDivider()

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Signature",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ─── Expandable: Thickness Slider ───
            AnimatedVisibility(visible = showThicknessPanel) {
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
                        text = "${currentStrokeWidth.toInt()}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = currentStrokeWidth,
                        onValueChange = onStrokeWidthChange,
                        valueRange = 2f..20f,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor
                        )
                    )
                    // Live preview
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = currentColor,
                            radius = currentStrokeWidth / 2f
                        )
                    }
                }
            }

            // ─── Expandable: Color Picker ───
            AnimatedVisibility(visible = showColorPanel) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    signatureColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (color == currentColor) {
                                        Modifier.border(
                                            2.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                    }
                                )
                                .pointerInput(color) {
                                    detectTapGestures {
                                        onColorChange(color)
                                    }
                                }
                        )
                    }
                }
            }

            // ─── Expandable: Opacity Slider ───
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
private fun SignatureToolbarDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    )
}
