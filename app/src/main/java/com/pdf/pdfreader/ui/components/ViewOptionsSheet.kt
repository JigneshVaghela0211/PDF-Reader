package com.pdf.pdfreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BackgroundOption(
                    label = "Original",
                    color = Color.White,
                    borderColor = Color.LightGray,
                    isSelected = viewSettings.backgroundMode == BackgroundMode.ORIGINAL,
                    onClick = { onSettingsChange(viewSettings.copy(backgroundMode = BackgroundMode.ORIGINAL)) }
                )
                BackgroundOption(
                    label = "Paper",
                    color = Color(0xFFF5F0E1),
                    borderColor = Color(0xFFD4C9A8),
                    isSelected = viewSettings.backgroundMode == BackgroundMode.PAPER,
                    onClick = { onSettingsChange(viewSettings.copy(backgroundMode = BackgroundMode.PAPER)) }
                )
                BackgroundOption(
                    label = "Eye Care",
                    color = Color(0xFFF8E8C8),
                    borderColor = Color(0xFFD4A96E),
                    isSelected = viewSettings.backgroundMode == BackgroundMode.EYE_COMFORT,
                    onClick = { onSettingsChange(viewSettings.copy(backgroundMode = BackgroundMode.EYE_COMFORT)) }
                )
                BackgroundOption(
                    label = "Invert",
                    color = Color(0xFF1A1A1A),
                    borderColor = Color(0xFF444444),
                    isSelected = viewSettings.backgroundMode == BackgroundMode.INVERT,
                    onClick = { onSettingsChange(viewSettings.copy(backgroundMode = BackgroundMode.INVERT)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Section 3: Switches ────────────────────
            SectionLabel(text = "Options")
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Page by Page",
                subtitle = "Snap to page edges",
                checked = viewSettings.isPageSnap,
                onCheckedChange = { onSettingsChange(viewSettings.copy(isPageSnap = it)) }
            )

            SwitchRow(
                icon = Icons.Default.LightMode,
                label = "Keep Screen On",
                subtitle = "Prevent screen from sleeping",
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
private fun BackgroundOption(
    label: String,
    color: Color,
    borderColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color == Color(0xFF1A1A1A)) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
