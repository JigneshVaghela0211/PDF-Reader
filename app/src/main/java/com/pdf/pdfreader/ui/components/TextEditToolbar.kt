package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact toolbar for text editing operations.
 * Provides font size adjustment, color selection, confirm/cancel actions.
 */
@Composable
fun TextEditToolbar(
    fontSize: Float,
    color: Color,
    onFontSizeChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val colorOptions = remember {
        listOf(
            Color.Black,
            Color.Red,
            Color.Blue,
            Color(0xFF1B5E20), // Dark Green
            Color(0xFFFF6F00), // Amber
            Color(0xFF6A1B9A), // Purple
            Color.DarkGray,
            Color.White
        )
    }

    var showColors by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 1: Font size + Color toggle + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font size controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onFontSizeChange((fontSize - 1f).coerceAtLeast(6f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.TextDecrease,
                        contentDescription = "Decrease Font",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "${fontSize.toInt()}pt",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { onFontSizeChange((fontSize + 1f).coerceAtMost(72f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = "Increase Font",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Current color indicator / toggle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { showColors = !showColors }
                )
            }

            // Confirm / Cancel
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Row 2: Color palette (expandable)
        if (showColors) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                colorOptions.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (c == color) 2.5.dp else 0.5.dp,
                                color = if (c == color) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable {
                                onColorChange(c)
                                showColors = false
                            }
                    )
                }
            }
        }
    }
}
