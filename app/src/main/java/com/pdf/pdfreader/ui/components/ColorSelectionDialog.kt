package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorSelectionDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    /** When true, show an opacity slider and bake the chosen alpha into the result. */
    showOpacity: Boolean = false,
    /** Recently-used colors shown as a quick-pick row (newest first). Empty = hidden. */
    recentColors: List<Color> = emptyList()
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    var alpha by remember { mutableFloatStateOf(if (showOpacity) initialColor.alpha else 1f) }

    val predefinedColors = listOf(
        Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), // Yellow, Green, Blue
        Color(0xFFEC407A), Color(0xFFFB8C00), Color(0xFFE53935), // Pink, Orange, Red
        Color(0xFF8E24AA), Color(0xFF1C1B1F), Color(0xFF00ACC1), // Purple, Black, Cyan
        Color(0xFF6D4C41), Color(0xFF9E9E9E), Color(0xFFFFFFFF)  // Brown, Grey, White
    )

    // Result color: alpha only participates when the opacity slider is shown.
    val currentColor = if (showOpacity) Color(red, green, blue, alpha) else Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Select Color",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Recent colors (newest first) — quick re-pick
                if (recentColors.isNotEmpty()) {
                    Text(
                        text = "RECENT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentColors.take(8).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable {
                                        red = color.red
                                        green = color.green
                                        blue = color.blue
                                        if (showOpacity) alpha = color.alpha
                                    }
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }

                // Predefined colors label
                Text(
                    text = "PREDEFINED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )

                // 6-column color grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    predefinedColors.chunked(6).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { color ->
                                val isSelected = red == color.red && green == color.green && blue == color.blue
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(
                                            if (isSelected)
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            else Modifier
                                        )
                                        .padding(if (isSelected) 4.dp else 0.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (!isSelected && color == Color.White)
                                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            else Modifier
                                        )
                                        .clickable {
                                            red = color.red
                                            green = color.green
                                            blue = color.blue
                                        }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // Custom Color Mix label
                Text(
                    text = "CUSTOM COLOR MIX",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )

                // Color preview + sliders
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ColorSliderRow(
                            label = "R",
                            labelColor = Color(0xFFE53935),
                            value = red,
                            trackBrush = Brush.horizontalGradient(listOf(Color.Black, Color.Red)),
                            thumbColor = Color(0xFFE53935),
                            onValueChange = { red = it }
                        )
                        ColorSliderRow(
                            label = "G",
                            labelColor = Color(0xFF43A047),
                            value = green,
                            trackBrush = Brush.horizontalGradient(listOf(Color.Black, Color(0xFF43A047))),
                            thumbColor = Color(0xFF43A047),
                            onValueChange = { green = it }
                        )
                        ColorSliderRow(
                            label = "B",
                            labelColor = Color(0xFF1E88E5),
                            value = blue,
                            trackBrush = Brush.horizontalGradient(listOf(Color.Black, Color(0xFF1E88E5))),
                            thumbColor = Color(0xFF1E88E5),
                            onValueChange = { blue = it }
                        )
                    }
                }

                // Opacity — only for markup where transparency matters.
                if (showOpacity) {
                    Text(
                        text = "OPACITY  ${(alpha * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    ColorSliderRow(
                        label = "α",
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        value = alpha,
                        trackBrush = Brush.horizontalGradient(
                            listOf(Color.White, Color(red, green, blue))
                        ),
                        thumbColor = Color(red, green, blue),
                        onValueChange = { alpha = it.coerceIn(0.1f, 1f) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentColor)
                    onDismiss()
                },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    labelColor: Color,
    value: Float,
    trackBrush: Brush,
    thumbColor: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            modifier = Modifier.width(10.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = thumbColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
