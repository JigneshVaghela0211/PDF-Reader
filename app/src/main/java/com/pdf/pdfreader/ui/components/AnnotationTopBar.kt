package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToolChange(AnnotationTool.PEN) }) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = "Pen",
                        tint = if (currentTool == AnnotationTool.PEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { onToolChange(AnnotationTool.HIGHLIGHTER) }) {
                    Icon(
                        Icons.Default.Highlight,
                        contentDescription = "Highlighter",
                        tint = if (currentTool == AnnotationTool.HIGHLIGHTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Text button
                IconButton(onClick = { onToolChange(AnnotationTool.TEXT) }) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = "Text Note",
                        tint = if (currentTool == AnnotationTool.TEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Eraser button
                IconButton(onClick = { onToolChange(AnnotationTool.ERASER) }) {
                    Icon(
                        Icons.Default.Clear, 
                        contentDescription = "Eraser",
                        tint = if (currentTool == AnnotationTool.ERASER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Color Picker
                val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorChange(color) }
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
}
