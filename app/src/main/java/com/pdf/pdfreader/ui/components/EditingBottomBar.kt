package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Modern bottom editing toolbar for the PDF editor.
 * 
 * Architecture:
 * - Primary bar: 5 main tools (Select, Text, Image, Signature, More)
 * - "More" expands a grouped options panel from the bottom
 * - Clean, scalable, Material 3 design
 */
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
    var showMorePanel by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            // ─── Expandable "More" Panel ───
            AnimatedVisibility(
                visible = showMorePanel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                MoreOptionsPanel(
                    currentTool = currentTool,
                    onToolChange = {
                        onToolChange(it)
                        showMorePanel = false
                    },
                    onDismiss = { showMorePanel = false }
                )
            }

            // ─── Primary Bottom Bar ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo
                    BottomBarItem(
                        icon = Icons.Default.Undo,
                        label = "Undo",
                        isSelected = false,
                        enabled = canUndo,
                        onClick = onUndoClick
                    )

                    // Redo
                    BottomBarItem(
                        icon = Icons.Default.Redo,
                        label = "Redo",
                        isSelected = false,
                        enabled = canRedo,
                        onClick = onRedoClick
                    )

                    // Divider
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Pen
                    BottomBarItem(
                        icon = Icons.Default.Brush,
                        label = "Pen",
                        isSelected = currentTool == AnnotationTool.PEN,
                        onClick = {
                            onToolChange(
                                if (currentTool == AnnotationTool.PEN) AnnotationTool.NONE
                                else AnnotationTool.PEN
                            )
                        }
                    )

                    // Text
                    BottomBarItem(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isSelected = currentTool == AnnotationTool.TEXT,
                        onClick = {
                            onToolChange(
                                if (currentTool == AnnotationTool.TEXT) AnnotationTool.NONE
                                else AnnotationTool.TEXT
                            )
                        }
                    )

                    // Image
                    BottomBarItem(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Image",
                        isSelected = currentTool == AnnotationTool.INSERT_IMAGE,
                        onClick = {
                            onToolChange(
                                if (currentTool == AnnotationTool.INSERT_IMAGE) AnnotationTool.NONE
                                else AnnotationTool.INSERT_IMAGE
                            )
                        }
                    )

                    // Signature
                    BottomBarItem(
                        icon = Icons.Default.Draw,
                        label = "Sign",
                        isSelected = false,
                        onClick = onSignatureClick
                    )

                    // Color indicator
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER)
                                    currentColor
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(onClick = onColorClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Color",
                            tint = if (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER)
                                Color.White
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // More
                    BottomBarItem(
                        icon = Icons.Default.MoreHoriz,
                        label = "More",
                        isSelected = showMorePanel,
                        onClick = { showMorePanel = !showMorePanel }
                    )
                }
            }
        }
    }
}

/**
 * Expandable panel showing grouped tool categories.
 */
@Composable
private fun MoreOptionsPanel(
    currentTool: AnnotationTool,
    onToolChange: (AnnotationTool) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header with drag handle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Drawing Group ───
            Text(
                "Drawing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MoreOptionChip(
                    icon = Icons.Default.Brush,
                    label = "Pen",
                    isSelected = currentTool == AnnotationTool.PEN,
                    onClick = { onToolChange(AnnotationTool.PEN) }
                )
                MoreOptionChip(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    isSelected = currentTool == AnnotationTool.HIGHLIGHTER,
                    onClick = { onToolChange(AnnotationTool.HIGHLIGHTER) }
                )
                MoreOptionChip(
                    icon = Icons.Default.CleaningServices,
                    label = "Eraser",
                    isSelected = currentTool == AnnotationTool.ERASER,
                    onClick = { onToolChange(AnnotationTool.ERASER) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Content Group ───
            Text(
                "Content",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MoreOptionChip(
                    icon = Icons.Default.TextFields,
                    label = "Text Note",
                    isSelected = currentTool == AnnotationTool.TEXT,
                    onClick = { onToolChange(AnnotationTool.TEXT) }
                )
                MoreOptionChip(
                    icon = Icons.Default.EditNote,
                    label = "Edit Text",
                    isSelected = currentTool == AnnotationTool.EDIT_TEXT,
                    onClick = { onToolChange(AnnotationTool.EDIT_TEXT) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Single item in the primary bottom bar.
 */
@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isSelected) Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(10.dp)
                        )
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

/**
 * Chip-style option in the "More" panel.
 */
@Composable
private fun MoreOptionChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
