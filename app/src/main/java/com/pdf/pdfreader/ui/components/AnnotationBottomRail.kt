package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AnnotationBottomRail(
    modifier: Modifier = Modifier,
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit
) {
    var showStrokeSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorSelectionDialog(
            initialColor = currentColor,
            onColorSelected = { onColorChange(it) },
            onDismiss = { showColorPicker = false }
        )
    }

    LaunchedEffect(currentTool) {
        showStrokeSlider = currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Expandable Stroke Slider
        AnimatedVisibility(
            visible = showStrokeSlider,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = "Stroke width", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Slider(
                        value = currentStrokeWidth,
                        onValueChange = onStrokeWidthChange,
                        valueRange = 1f..20f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor,
                            inactiveTrackColor = currentColor.copy(alpha = 0.2f)
                        )
                    )
                    Text(
                        text = "${currentStrokeWidth.toInt()}pt",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Main Tool Rail
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToolButton(
                    icon = Icons.Default.Brush,
                    isActive = currentTool == AnnotationTool.PEN,
                    onClick = { onToolChange(if (currentTool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN) }
                )
                ToolButton(
                    icon = Icons.Default.Highlight,
                    isActive = currentTool == AnnotationTool.HIGHLIGHTER,
                    onClick = { onToolChange(if (currentTool == AnnotationTool.HIGHLIGHTER) AnnotationTool.NONE else AnnotationTool.HIGHLIGHTER) }
                )
                ToolButton(
                    icon = Icons.Default.TextFields,
                    isActive = currentTool == AnnotationTool.TEXT,
                    onClick = { onToolChange(if (currentTool == AnnotationTool.TEXT) AnnotationTool.NONE else AnnotationTool.TEXT) }
                )
                ToolButton(
                    icon = Icons.Default.CleaningServices,
                    isActive = currentTool == AnnotationTool.ERASER,
                    onClick = { onToolChange(if (currentTool == AnnotationTool.ERASER) AnnotationTool.NONE else AnnotationTool.ERASER) }
                )

                // Minimal Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )

                // Color Selection
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { showColorPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tintColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(3.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}
