package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Context-aware editing toolbar shown when a SIGNATURE element is selected.
 * Signature-specific controls (thickness, colour) plus standard image tools.
 *
 * Positioned by [AnchoredToolbar] (above/below the element, clear of resize handles,
 * centred, clamped on-screen) and laid out with [FlowRow] so all options stay visible.
 *
 * Anchor values are in viewport pixels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignatureEditToolbar(
    visible: Boolean,
    anchorCenterX: Int,
    anchorTop: Int,
    anchorBottom: Int,
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

    // Local slider state so the thumb moves immediately while dragging.
    // The viewmodel re-render is triggered only on drag-end (onValueChangeFinished)
    // so we don't launch dozens of heavy IO operations mid-drag.
    var localStrokeWidth by remember(currentStrokeWidth) { mutableFloatStateOf(currentStrokeWidth) }
    var localOpacity by remember(opacity) { mutableFloatStateOf(opacity) }

    val signatureColors = listOf(
        Color.Black,
        Color(0xFF1565C0), Color(0xFFD32F2F), Color(0xFF2E7D32),
        Color(0xFF6A1B9A), Color(0xFFEF6C00)
    )

    AnchoredToolbar(
        visible = visible,
        anchorCenterX = anchorCenterX,
        anchorTop = anchorTop,
        anchorBottom = anchorBottom
    ) { maxWidth ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.widthIn(max = maxWidth),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Signature-specific: thickness & colour
                    if (hasEditableStrokes) {
                        ToolButton(
                            icon = Icons.Default.Brush,
                            contentDescription = "Thickness",
                            enabled = !isLocked,
                            highlighted = showThicknessPanel,
                            onClick = {
                                showThicknessPanel = !showThicknessPanel
                                showColorPanel = false; showOpacitySlider = false
                            }
                        )
                        // Colour swatch button
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    showColorPanel = !showColorPanel
                                    showThicknessPanel = false; showOpacitySlider = false
                                },
                                enabled = !isLocked,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(currentColor)
                                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                        ToolbarDivider()
                    }

                    ToolButton(Icons.Default.RotateLeft, "Rotate Left", enabled = !isLocked, onClick = onRotateLeft)
                    ToolButton(Icons.Default.RotateRight, "Rotate Right", enabled = !isLocked, onClick = onRotateRight)
                    ToolbarDivider()
                    ToolButton(Icons.Default.ContentCopy, "Duplicate", onClick = onDuplicate)
                    ToolButton(Icons.Default.FlipToFront, "Bring to Front", onClick = onBringToFront)
                    ToolButton(Icons.Default.FlipToBack, "Send to Back", onClick = onSendToBack)
                    ToolbarDivider()
                    ToolButton(
                        icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = if (isLocked) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface,
                        highlighted = isLocked,
                        onClick = onToggleLock
                    )
                    ToolButton(
                        icon = Icons.Default.Opacity,
                        contentDescription = "Opacity",
                        highlighted = showOpacitySlider,
                        onClick = {
                            showOpacitySlider = !showOpacitySlider
                            showThicknessPanel = false; showColorPanel = false
                        }
                    )
                    ToolbarDivider()
                    ToolButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete Signature",
                        tint = Color(0xFFE53935),
                        onClick = onDelete
                    )
                }
            }

            // ─── Thickness panel ───
            AnimatedVisibility(visible = showThicknessPanel) {
                PanelSurface(maxWidth) {
                    Text(
                        text = "${localStrokeWidth.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = localStrokeWidth,
                        onValueChange = { localStrokeWidth = it },
                        onValueChangeFinished = { onStrokeWidthChange(localStrokeWidth) },
                        valueRange = 2f..20f,
                        modifier = Modifier.width(150.dp),
                        colors = SliderDefaults.colors(thumbColor = currentColor, activeTrackColor = currentColor)
                    )
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = currentColor, radius = localStrokeWidth / 2f)
                    }
                }
            }

            // ─── Colour panel ───
            AnimatedVisibility(visible = showColorPanel) {
                PanelSurface(maxWidth) {
                    signatureColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (color == currentColor)
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                )
                                .pointerInput(color) {
                                    detectTapGestures { onColorChange(color) }
                                }
                        )
                    }
                }
            }

            // ─── Opacity panel ───
            AnimatedVisibility(visible = showOpacitySlider) {
                PanelSurface(maxWidth) {
                    Text(
                        text = "${(localOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = localOpacity,
                        onValueChange = { localOpacity = it },
                        onValueChangeFinished = { onOpacityChange(localOpacity) },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.width(150.dp),
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

/** Shared rounded surface used by the expandable panels below the main pill. */
@Composable
private fun PanelSurface(maxWidth: Dp, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = maxWidth),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
