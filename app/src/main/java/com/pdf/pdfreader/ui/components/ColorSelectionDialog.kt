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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ColorSelectionDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)
    val predefinedColors = listOf(
        Color(0xFF004591), // Lumen Primary (Blue)
        Color(0xFFF44336), // Red
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF1E293B)  // Slate
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Custom Ink",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(currentColor, CircleShape)
                            .border(4.dp, currentColor.copy(alpha = 0.1f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    predefinedColors.forEach { color ->
                        val isSelected = currentColor == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    red = color.red
                                    green = color.green
                                    blue = color.blue
                                }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, color, CircleShape).padding(4.dp).border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // RGB Mixers
                ColorSliderLabel("RED", red)
                Slider(
                    value = red, 
                    onValueChange = { red = it }, 
                    colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red, inactiveTrackColor = Color.Red.copy(alpha=0.2f))
                )

                Spacer(modifier = Modifier.height(8.dp))

                ColorSliderLabel("GREEN", green)
                Slider(
                    value = green, 
                    onValueChange = { green = it }, 
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981), inactiveTrackColor = Color(0xFF10B981).copy(alpha=0.2f))
                )

                Spacer(modifier = Modifier.height(8.dp))

                ColorSliderLabel("BLUE", blue)
                Slider(
                    value = blue, 
                    onValueChange = { blue = it }, 
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF004591), activeTrackColor = Color(0xFF004591), inactiveTrackColor = Color(0xFF004591).copy(alpha=0.2f))
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Confirm Button
                Button(
                    onClick = {
                        onColorSelected(currentColor)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        "Confirm Selection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSliderLabel(name: String, value: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${(value * 255).toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
