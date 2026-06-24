package com.pdf.pdfreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // ─── Header ─────────────────────────────────
            Text(
                text = "View Options",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ─── Reading Direction ───────────────────────
            SheetSectionLabel("Reading Direction")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectionChip(
                    icon = Icons.Default.SwapVert,
                    label = "Vertical",
                    selected = viewSettings.readingMode == ReadingMode.VERTICAL,
                    onClick = { onSettingsChange(viewSettings.copy(readingMode = ReadingMode.VERTICAL)) },
                    modifier = Modifier.weight(1f)
                )
                DirectionChip(
                    icon = Icons.Default.SwapHoriz,
                    label = "Horizontal",
                    selected = viewSettings.readingMode == ReadingMode.HORIZONTAL,
                    onClick = { onSettingsChange(viewSettings.copy(readingMode = ReadingMode.HORIZONTAL)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ─── Background Mode ────────────────────────
            SheetSectionLabel("Background")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Original", BackgroundMode.ORIGINAL, Color(0xFFFFFFFF)),
                    Triple("Paper", BackgroundMode.PAPER, Color(0xFFF5F0E1)),
                    Triple("Eye Care", BackgroundMode.EYE_COMFORT, Color(0xFFF8E8C8)),
                    Triple("Invert", BackgroundMode.INVERT, Color(0xFF1C1B1F))
                ).forEach { (label, mode, swatch) ->
                    BgModeChip(
                        label = label,
                        swatch = swatch,
                        selected = viewSettings.backgroundMode == mode,
                        onClick = { onSettingsChange(viewSettings.copy(backgroundMode = mode)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Switches ───────────────────────────────
            SheetSectionLabel("Options")
            Spacer(Modifier.height(4.dp))
            SheetSwitchRow(
                label = "Page by Page",
                sublabel = "Snap scroll to each page",
                checked = viewSettings.isPageSnap,
                onCheckedChange = { onSettingsChange(viewSettings.copy(isPageSnap = it)) }
            )
            SheetSwitchRow(
                label = "Keep Screen On",
                sublabel = "Prevent sleep while reading",
                checked = viewSettings.keepScreenOn,
                onCheckedChange = { onSettingsChange(viewSettings.copy(keepScreenOn = it)) }
            )

            Spacer(Modifier.height(24.dp))

            // ─── Manage Pages ────────────────────────────
            Button(
                onClick = { onDismiss(); onManagePages() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Pages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun SheetSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun DirectionChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "dirBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dirFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            } else {
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(17.dp))
            }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

@Composable
private fun BgModeChip(
    label: String,
    swatch: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(swatch)
                .then(
                    if (selected)
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SheetSwitchRow(
    label: String,
    sublabel: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                sublabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
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
