package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationTopBar(
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    var showStrokeSlider by remember { mutableStateOf(false) }

    // Auto-show thickness slider when Pen or Highlighter is selected
    LaunchedEffect(currentTool) {
        showStrokeSlider = currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER
    }

    Column {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tool buttons
                    IconButton(onClick = { 
                        onToolChange(if (currentTool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN) 
                    }) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = "Pen",
                            tint = if (currentTool == AnnotationTool.PEN) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        onToolChange(if (currentTool == AnnotationTool.HIGHLIGHTER) AnnotationTool.NONE else AnnotationTool.HIGHLIGHTER) 
                    }) {
                        Icon(
                            Icons.Default.Highlight,
                            contentDescription = "Highlighter",
                            tint = if (currentTool == AnnotationTool.HIGHLIGHTER) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        onToolChange(if (currentTool == AnnotationTool.TEXT) AnnotationTool.NONE else AnnotationTool.TEXT) 
                    }) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = "Text Note",
                            tint = if (currentTool == AnnotationTool.TEXT) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        onToolChange(if (currentTool == AnnotationTool.ERASER) AnnotationTool.NONE else AnnotationTool.ERASER) 
                    }) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Eraser",
                            tint = if (currentTool == AnnotationTool.ERASER) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Color Picker with selection indicator
                    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (color == currentColor)
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else
                                        Modifier.border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                )
                                .clickable { onColorChange(color) }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Stroke width toggle button
                    if (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showStrokeSlider) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { showStrokeSlider = !showStrokeSlider },
                            contentAlignment = Alignment.Center
                        ) {
                            // Show current stroke as a dot
                            Box(
                                modifier = Modifier
                                    .size((currentStrokeWidth.coerceIn(3f, 16f)).dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Edit Mode")
                }
            },
            actions = {
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save to PDF")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Stroke width slider (expandable)
        if (showStrokeSlider && (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stroke",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = currentStrokeWidth,
                        onValueChange = onStrokeWidthChange,
                        valueRange = 1f..20f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor
                        )
                    )
                    Text(
                        text = "${currentStrokeWidth.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
