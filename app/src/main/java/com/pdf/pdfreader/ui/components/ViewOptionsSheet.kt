package com.pdf.pdfreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.domain.model.BackgroundMode
import com.pdf.pdfreader.domain.model.ReadingMode
import com.pdf.pdfreader.domain.model.ViewSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewOptionsSheet(
    viewSettings: ViewSettings,
    onSettingsChange: (ViewSettings) -> Unit,
    onManagePages: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ─── Header ─────────────────────────────────
            Text(
                text = "View Options",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // ─── Section 1: Reading Direction ───────────
            SectionLabel(text = "Reading Direction")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DirectionCard(
                    icon = Icons.Default.SwapVert,
                    label = "Vertical",
                    isSelected = viewSettings.readingMode == ReadingMode.VERTICAL,
                    onClick = { onSettingsChange(viewSettings.copy(readingMode = ReadingMode.VERTICAL)) },
                    modifier = Modifier.weight(1f)
                )
                DirectionCard(
                    icon = Icons.Default.SwapHoriz,
                    label = "Horizontal",
                    isSelected = viewSettings.readingMode == ReadingMode.HORIZONTAL,
                    onClick = { onSettingsChange(viewSettings.copy(readingMode = ReadingMode.HORIZONTAL)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Section 2: Background Modes ────────────
            SectionLabel(text = "Background")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Original" to BackgroundMode.ORIGINAL,
                    "Paper" to BackgroundMode.PAPER,
                    "Eye Care" to BackgroundMode.EYE_COMFORT,
                    "Invert" to BackgroundMode.INVERT
                ).forEach { (label, mode) ->
                    val selected = viewSettings.backgroundMode == mode
                    BackgroundChip(
                        label = label,
                        selected = selected,
                        onClick = { onSettingsChange(viewSettings.copy(backgroundMode = mode)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Section 3: Switches ────────────────────
            SectionLabel(text = "Options")
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                label = "Page by Page",
                checked = viewSettings.isPageSnap,
                onCheckedChange = { onSettingsChange(viewSettings.copy(isPageSnap = it)) }
            )

            SwitchRow(
                label = "Keep Screen On",
                checked = viewSettings.keepScreenOn,
                onCheckedChange = { onSettingsChange(viewSettings.copy(keepScreenOn = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // ─── Manage Pages Button ────────────────────
            Button(
                onClick = {
                    onDismiss()
                    onManagePages()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Pages", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Reusable Sub-components ────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun DirectionCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "dirBg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dirContent"
    )

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
