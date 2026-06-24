package com.pdf.pdfreader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ToolbarBg = Color(0xFF1C1B1F)
private val ToolbarDivider = Color(0xFF444444)
private val ToolbarText = Color.White

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
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val clampedX = offsetX.coerceIn(8, with(density) { (screenWidth * density.density).toInt() } - 320)

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedX, offsetY) }
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .background(ToolbarBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionAction(Icons.Default.ContentCopy, "Copy", onCopy)
                SelectionDivider()
                SelectionAction(Icons.Default.Edit, "Edit", onEdit)
                SelectionDivider()
                SelectionAction(Icons.Default.Highlight, "Highlight", onHighlight, tint = Color(0xFFFFD54A))
                SelectionDivider()
                SelectionAction(Icons.Default.FormatUnderlined, "Underline", onUnderline)
                SelectionDivider()
                SelectionAction(Icons.Default.FormatStrikethrough, "Strike", onStrikethrough)
            }
        }
    }
}

@Composable
private fun SelectionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(ToolbarDivider)
    )
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = ToolbarText
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = tint
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = ToolbarText
            )
        }
    }
}
