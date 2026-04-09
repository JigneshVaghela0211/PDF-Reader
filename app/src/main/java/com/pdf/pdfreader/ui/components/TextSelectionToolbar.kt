package com.pdf.pdfreader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun TextSelectionToolbar(
    visible: Boolean,
    offsetX: Int,
    offsetY: Int,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikethrough: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f)
    ) {
        // Clamp bounds to prevent toolbar from going off-screen
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val clampedX = offsetX.coerceIn(20, with(density) { (screenWidth * density.density).toInt() } - 300)

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedX, offsetY) }
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextSelectionToolbarButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    onClick = onCopy
                )
                
                Divider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                TextSelectionToolbarButton(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    onClick = onEdit
                )

                Divider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

                TextSelectionToolbarButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    onClick = onHighlight
                )
                
                TextSelectionToolbarButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    onClick = onUnderline
                )
                
                TextSelectionToolbarButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strike",
                    onClick = onStrikethrough
                )
            }
        }
    }
}

@Composable
private fun TextSelectionToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
