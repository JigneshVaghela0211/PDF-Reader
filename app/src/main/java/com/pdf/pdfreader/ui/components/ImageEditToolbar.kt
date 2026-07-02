package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Polished, self-positioning toolbar shown when an image element is selected.
 *
 * - Positioned by [AnchoredToolbar]: above the image (or below when there's no room),
 *   clear of the resize handles, centred, and always fully on-screen.
 * - Options are laid out in a [FlowRow] so they wrap to the available width and every
 *   action stays visible/reachable on any screen size.
 * - Actions are grouped (transform · arrange · layer · state · delete) with dividers.
 *
 * Anchor values are in viewport pixels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageEditToolbar(
    visible: Boolean,
    anchorCenterX: Int,
    anchorTop: Int,
    anchorBottom: Int,
    isLocked: Boolean = false,
    opacity: Float = 1f,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    onBringToFront: () -> Unit = {},
    onSendToBack: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onOpacityChange: (Float) -> Unit = {},
    onSnapToCenter: () -> Unit = {},
    onFlipHorizontal: () -> Unit = {},
    onFlipVertical: () -> Unit = {}
) {
    var showOpacitySlider by remember { mutableStateOf(false) }

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
                    // Transform
                    ToolButton(Icons.Default.RotateLeft, "Rotate Left", enabled = !isLocked, onClick = onRotateLeft)
                    ToolButton(Icons.Default.RotateRight, "Rotate Right", enabled = !isLocked, onClick = onRotateRight)
                    ToolButton(Icons.Default.Flip, "Flip Horizontal", enabled = !isLocked, onClick = onFlipHorizontal)
                    ToolButton(Icons.Default.SwapVert, "Flip Vertical", enabled = !isLocked, onClick = onFlipVertical)
                    ToolbarDivider()
                    // Arrange
                    ToolButton(Icons.Default.ContentCopy, "Duplicate", onClick = onDuplicate)
                    ToolButton(Icons.Default.CenterFocusStrong, "Center", enabled = !isLocked, onClick = onSnapToCenter)
                    ToolbarDivider()
                    // Layer
                    ToolButton(Icons.Default.FlipToFront, "Bring to Front", onClick = onBringToFront)
                    ToolButton(Icons.Default.FlipToBack, "Send to Back", onClick = onSendToBack)
                    ToolbarDivider()
                    // State
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
                        onClick = { showOpacitySlider = !showOpacitySlider }
                    )
                    ToolbarDivider()
                    // Destructive
                    ToolButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete Image",
                        tint = Color(0xFFE53935),
                        onClick = onDelete
                    )
                }
            }

            AnimatedVisibility(visible = showOpacitySlider) {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Opacity,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = opacity,
                            onValueChange = onOpacityChange,
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
}

/** A single icon action on the toolbar, with an optional tonal highlight pill. */
@Composable
internal fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val resolvedTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> tint
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(
                if (highlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
            Icon(icon, contentDescription = contentDescription, tint = resolvedTint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
