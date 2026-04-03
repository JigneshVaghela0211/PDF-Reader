package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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

    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(Color(0xFF9C27B0)) } // Default custom is purple

    if (showColorPicker) {
        CustomColorPickerDialog(
            initialColor = currentColor,
            onColorSelected = { 
                customColor = it
                onColorChange(it) 
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Auto-show thickness slider when Pen or Highlighter is selected
    LaunchedEffect(currentTool) {
        showStrokeSlider = currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER
    }

    Column {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Extracted components for better codebase management
                    AnnotationToolButtons(currentTool, onToolChange)

                    Spacer(modifier = Modifier.width(8.dp))

                    ColorPaletteMenu(
                        currentColor = currentColor,
                        customColor = customColor,
                        onColorChange = onColorChange,
                        onCustomColorClick = { showColorPicker = true }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER) {
                        StrokeWidthDot(
                            currentStrokeWidth = currentStrokeWidth,
                            currentColor = currentColor,
                            showStrokeSlider = showStrokeSlider,
                            onToggle = { showStrokeSlider = !showStrokeSlider }
                        )
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
            StrokeWidthSliderExpandable(currentStrokeWidth, currentColor, onStrokeWidthChange)
        }
    }
}

// ==========================================
// Extracted Components for Code Separation
// ==========================================

@Composable
fun AnnotationToolButtons(
    currentTool: AnnotationTool,
    onToolChange: (AnnotationTool) -> Unit
) {
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN) }) {
        Icon(Icons.Default.Brush, contentDescription = "Pen", tint = if (currentTool == AnnotationTool.PEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.HIGHLIGHTER) AnnotationTool.NONE else AnnotationTool.HIGHLIGHTER) }) {
        Icon(Icons.Default.Highlight, contentDescription = "Highlighter", tint = if (currentTool == AnnotationTool.HIGHLIGHTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.TEXT) AnnotationTool.NONE else AnnotationTool.TEXT) }) {
        Icon(Icons.Default.TextFields, contentDescription = "Text Note", tint = if (currentTool == AnnotationTool.TEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.ERASER) AnnotationTool.NONE else AnnotationTool.ERASER) }) {
        Icon(Icons.Default.CleaningServices, contentDescription = "Eraser", tint = if (currentTool == AnnotationTool.ERASER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ColorPaletteMenu(
    currentColor: Color,
    customColor: Color,
    onColorChange: (Color) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val predefinedColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
    predefinedColors.forEach { color ->
        Box(
            modifier = Modifier
                .size(28.dp)
                .padding(2.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (color == currentColor) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier.border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                )
                .clickable { onColorChange(color) }
        )
    }
    
    val isCustomSelected = currentColor !in predefinedColors
    Box(
        modifier = Modifier
            .size(28.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(customColor)
            .then(
                if (isCustomSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier.border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
            )
            .clickable {
                if (isCustomSelected) onCustomColorClick() else onColorChange(customColor)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Palette, contentDescription = "Custom Color", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun StrokeWidthDot(
    currentStrokeWidth: Float,
    currentColor: Color,
    showStrokeSlider: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (showStrokeSlider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((currentStrokeWidth.coerceIn(3f, 16f)).dp)
                .clip(CircleShape)
                .background(currentColor)
        )
    }
}

@Composable
fun StrokeWidthSliderExpandable(
    currentStrokeWidth: Float,
    currentColor: Color,
    onStrokeWidthChange: (Float) -> Unit
) {
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

@Composable
fun CustomColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(red, green, blue), RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Red", color = Color.Red, style = MaterialTheme.typography.labelMedium)
                Slider(value = red, onValueChange = { red = it }, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
                
                Text("Green", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelMedium)
                Slider(value = green, onValueChange = { green = it }, colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50)))
                
                Text("Blue", color = Color.Blue, style = MaterialTheme.typography.labelMedium)
                Slider(value = blue, onValueChange = { blue = it }, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onColorSelected(Color(red, green, blue))
                onDismiss()
            }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
