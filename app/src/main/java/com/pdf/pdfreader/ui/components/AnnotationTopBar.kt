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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationTopBar(
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    hasEditableOverlays: Boolean = false,
    isExporting: Boolean = false,
    onExport: () -> Unit = {},
    onSignatureClick: () -> Unit = {}
) {
    var showStrokeSlider by remember { mutableStateOf(false) }

    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorSelectionDialog(
            initialColor = currentColor,
            onColorSelected = { 
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
                    // Undo button
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Redo button
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tool buttons
                    AnnotationToolButtons(
                        currentTool = currentTool,
                        onToolChange = onToolChange,
                        onSignatureClick = onSignatureClick
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ColorSelectionButton(
                        currentColor = currentColor,
                        onColorClick = { showColorPicker = true }
                    )

                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Edit Mode")
                }
            },
            actions = {
                // Export button (visible when text/image overlays exist)
                if (hasEditableOverlays) {
                    IconButton(
                        onClick = onExport,
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Export Edited PDF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save Annotations")
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
    onToolChange: (AnnotationTool) -> Unit,
    onSignatureClick: () -> Unit
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
    // Vertical divider between annotation tools and editor tools
    Spacer(modifier = Modifier.width(2.dp))
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    )
    Spacer(modifier = Modifier.width(2.dp))
    // Edit existing text
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.EDIT_TEXT) AnnotationTool.NONE else AnnotationTool.EDIT_TEXT) }) {
        Icon(Icons.Default.EditNote, contentDescription = "Edit Text", tint = if (currentTool == AnnotationTool.EDIT_TEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    // Insert image
    IconButton(onClick = { onToolChange(if (currentTool == AnnotationTool.INSERT_IMAGE) AnnotationTool.NONE else AnnotationTool.INSERT_IMAGE) }) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Insert Image", tint = if (currentTool == AnnotationTool.INSERT_IMAGE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
    // Signature
    IconButton(onClick = onSignatureClick) {
        Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_edit), contentDescription = "Signature", tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ColorSelectionButton(
    currentColor: Color,
    onColorClick: () -> Unit
) {
    val isLight = (currentColor.red + currentColor.green + currentColor.blue) > 2f
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(currentColor)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onColorClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = "Color Picker",
            tint = if (isLight) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
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

/**
 * Design-spec annotation bar: undo | redo | "Editing" | close | [Save]
 * followed by a scrollable tool-icon row and (when pen/highlight active) a stroke slider.
 */
@Composable
fun AnnotationTopBarDesign(
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    isExporting: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onColorClick: () -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onSignatureClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Column {
        // ── Row 1: undo · redo · "Editing" · close · Save ──────────────
        Surface(
            color = surfaceColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Editing",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(22.dp))
                }
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Surface(
                        onClick = onSave,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
    }
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val bgColor = if (active) activeColor.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            tint = if (active) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
