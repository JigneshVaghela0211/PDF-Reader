package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Floating toolbar displayed when an image element is selected.
 * Provides: Rotate Left (-90°), Rotate Right (+90°), Delete.
 */
@Composable
fun ImageEditToolbar(
    visible: Boolean,
    offsetX: Int,
    offsetY: Int,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) }
        ) {
            Row(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(24.dp))
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Rotate Left
                IconButton(
                    onClick = onRotateLeft,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RotateLeft,
                        contentDescription = "Rotate Left",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Rotate Right
                IconButton(
                    onClick = onRotateRight,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = "Rotate Right",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Image",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
