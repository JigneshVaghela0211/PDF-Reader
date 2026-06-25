package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.core.model.FeatureBadge
import com.pdf.pdfreader.presentation.editor.ToolbarFeatureProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditingBottomBar(
    visible: Boolean,
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onSignatureClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onColorClick: () -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 1.dp,
            modifier = modifier
        ) {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // Stroke slider — only for Pen and Highlighter
                if (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.LineWeight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Slider(
                            value = currentStrokeWidth,
                            onValueChange = onStrokeWidthChange,
                            valueRange = 1f..20f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Text(
                            text = "${currentStrokeWidth.toInt()} px",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }

                // Flat scrollable tool row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Each tool is gated by PdfEditorFeatureConfig via the provider — no
                    // hardcoded availability logic lives here. A DISABLED feature is omitted
                    // entirely; COMING_SOON/PREMIUM render but are not clickable.
                    FeatureToolButton(
                        feature = EditorFeature.FREEHAND_DRAWING,
                        icon = Icons.Default.Brush,
                        label = "Pen",
                        active = currentTool == AnnotationTool.PEN,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.HIGHLIGHT,
                        icon = Icons.Default.Highlight,
                        label = "Highlight",
                        active = currentTool == AnnotationTool.HIGHLIGHTER,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.HIGHLIGHTER) AnnotationTool.NONE else AnnotationTool.HIGHLIGHTER) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.ADD_TEXT,
                        icon = Icons.Default.TextFields,
                        label = "Note",
                        active = currentTool == AnnotationTool.TEXT,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.TEXT) AnnotationTool.NONE else AnnotationTool.TEXT) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.ERASER,
                        icon = Icons.Default.CleaningServices,
                        label = "Eraser",
                        active = currentTool == AnnotationTool.ERASER,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.ERASER) AnnotationTool.NONE else AnnotationTool.ERASER) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.EDIT_TEXT,
                        icon = Icons.Default.Title,
                        label = "Text",
                        active = currentTool == AnnotationTool.EDIT_TEXT,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.EDIT_TEXT) AnnotationTool.NONE else AnnotationTool.EDIT_TEXT) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.INSERT_IMAGE,
                        icon = Icons.Default.Image,
                        label = "Image",
                        active = currentTool == AnnotationTool.INSERT_IMAGE,
                        onActivate = { onToolChange(if (currentTool == AnnotationTool.INSERT_IMAGE) AnnotationTool.NONE else AnnotationTool.INSERT_IMAGE) }
                    )
                    FeatureToolButton(
                        feature = EditorFeature.SIGNATURE,
                        icon = Icons.Default.Draw,
                        label = "Sign",
                        active = false,
                        onActivate = onSignatureClick
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(onClick = onColorClick),
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }
        }
    }
}

/**
 * A tool button whose visibility/availability is driven entirely by
 * [PdfEditorFeatureConfig] (via [ToolbarFeatureProvider]). DISABLED features render
 * nothing; COMING_SOON/PREMIUM render with a badge and ignore taps.
 */
@Composable
private fun FeatureToolButton(
    feature: EditorFeature,
    icon: ImageVector,
    label: String,
    active: Boolean,
    onActivate: () -> Unit
) {
    val model = remember(feature) { ToolbarFeatureProvider.uiModel(feature) }
    if (!model.visible) return
    BottomToolButton(
        icon = icon,
        label = label,
        active = active,
        enabled = model.interactive,
        badge = model.badge,
        onClick = { if (model.interactive) onActivate() }
    )
}

@Composable
private fun BottomToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: FeatureBadge = FeatureBadge.NONE
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val bgColor = if (active) activeColor.copy(alpha = 0.12f) else Color.Transparent
    val contentAlpha = if (enabled) 1f else 0.4f

    Box {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(contentAlpha)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (active) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
        FeatureBadgeDecoration(badge)
    }
}
