package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Floating, rounded Material 3 editing toolbar for the PDF editor.
 *
 * Design:
 * - A single floating, rounded, elevated bar detached from the screen edges.
 * - Edit tools are organized into GROUPS (Draw, Text, Insert). Tapping a group
 *   reveals a floating rounded panel above the bar with that group's tools.
 * - Undo / Redo and the active colour live directly on the bar for quick access.
 *
 * The public signature is unchanged, so the call site needs no edits.
 */

/** Logical grouping of the editing tools shown on the bar. */
private enum class EditGroup { DRAW, TEXT, INSERT }

@Composable
fun EditingBottomBar(
    visible: Boolean,
    currentTool: AnnotationTool,
    currentColor: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onSignatureClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onColorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedGroup by remember { mutableStateOf<EditGroup?>(null) }

    // Collapse any open group when the bar is hidden.
    LaunchedEffect(visible) { if (!visible) expandedGroup = null }

    val drawActive = currentTool == AnnotationTool.PEN ||
        currentTool == AnnotationTool.HIGHLIGHTER ||
        currentTool == AnnotationTool.ERASER
    val textActive = currentTool == AnnotationTool.TEXT ||
        currentTool == AnnotationTool.EDIT_TEXT
    val insertActive = currentTool == AnnotationTool.INSERT_IMAGE

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Floating group panel (appears above the bar) ───
            AnimatedVisibility(
                visible = expandedGroup != null,
                enter = fadeIn() + scaleIn(initialScale = 0.92f) + slideInVertically { it / 3 },
                exit = fadeOut() + scaleOut(targetScale = 0.92f) + slideOutVertically { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GroupPanel(
                        group = expandedGroup,
                        currentTool = currentTool,
                        onToolSelected = {
                            onToolChange(it)
                            expandedGroup = null
                        },
                        onSignatureClick = {
                            onSignatureClick()
                            expandedGroup = null
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ─── Primary floating bar ───
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryButton(Icons.Default.Undo, "Undo", canUndo, onUndoClick)
                    HistoryButton(Icons.Default.Redo, "Redo", canRedo, onRedoClick)

                    BarDivider()

                    GroupButton(
                        icon = Icons.Default.Brush,
                        label = "Draw",
                        active = drawActive,
                        expanded = expandedGroup == EditGroup.DRAW,
                        modifier = Modifier.weight(1f)
                    ) {
                        expandedGroup = if (expandedGroup == EditGroup.DRAW) null else EditGroup.DRAW
                    }
                    GroupButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        active = textActive,
                        expanded = expandedGroup == EditGroup.TEXT,
                        modifier = Modifier.weight(1f)
                    ) {
                        expandedGroup = if (expandedGroup == EditGroup.TEXT) null else EditGroup.TEXT
                    }
                    GroupButton(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Insert",
                        active = insertActive,
                        expanded = expandedGroup == EditGroup.INSERT,
                        modifier = Modifier.weight(1f)
                    ) {
                        expandedGroup = if (expandedGroup == EditGroup.INSERT) null else EditGroup.INSERT
                    }

                    BarDivider()

                    // Active colour swatch
                    val colorActive = currentTool == AnnotationTool.PEN ||
                        currentTool == AnnotationTool.HIGHLIGHTER
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (colorActive) currentColor
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(onClick = onColorClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Color",
                            tint = if (colorActive) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Floating rounded panel listing the tools for the expanded group. */
@Composable
private fun GroupPanel(
    group: EditGroup?,
    currentTool: AnnotationTool,
    onToolSelected: (AnnotationTool) -> Unit,
    onSignatureClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            val title = when (group) {
                EditGroup.DRAW -> "Drawing tools"
                EditGroup.TEXT -> "Text tools"
                EditGroup.INSERT -> "Insert"
                null -> ""
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (group) {
                    EditGroup.DRAW -> {
                        ToolChip(Icons.Default.Brush, "Pen",
                            currentTool == AnnotationTool.PEN, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.PEN))
                        }
                        ToolChip(Icons.Default.Highlight, "Highlight",
                            currentTool == AnnotationTool.HIGHLIGHTER, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.HIGHLIGHTER))
                        }
                        ToolChip(Icons.Default.CleaningServices, "Eraser",
                            currentTool == AnnotationTool.ERASER, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.ERASER))
                        }
                    }
                    EditGroup.TEXT -> {
                        ToolChip(Icons.Default.TextFields, "Add Text",
                            currentTool == AnnotationTool.TEXT, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.TEXT))
                        }
                        ToolChip(Icons.Default.EditNote, "Edit Text",
                            currentTool == AnnotationTool.EDIT_TEXT, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.EDIT_TEXT))
                        }
                    }
                    EditGroup.INSERT -> {
                        ToolChip(Icons.Default.AddPhotoAlternate, "Image",
                            currentTool == AnnotationTool.INSERT_IMAGE, Modifier.weight(1f)) {
                            onToolSelected(toggle(currentTool, AnnotationTool.INSERT_IMAGE))
                        }
                        ToolChip(Icons.Default.Draw, "Signature",
                            isSelected = false, modifier = Modifier.weight(1f)) {
                            onSignatureClick()
                        }
                    }
                    null -> {}
                }
            }
        }
    }
}

/** Toggle helper: tapping the active tool again clears it back to NONE. */
private fun toggle(current: AnnotationTool, tool: AnnotationTool): AnnotationTool =
    if (current == tool) AnnotationTool.NONE else tool

/** A grouped category button on the primary bar. */
@Composable
private fun GroupButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val highlighted = active || expanded
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "chevron"
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1
            )
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}

/** Undo / Redo round icon button. */
@Composable
private fun HistoryButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Thin vertical divider used to separate sections on the bar. */
@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(26.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** A tool chip used inside the expanded group panel. */
@Composable
private fun ToolChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
        }
    }
}
