package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.R
import com.pdf.pdfreader.ui.viewmodel.SortOrder
import com.pdf.pdfreader.ui.viewmodel.SortType
import com.pdf.pdfreader.ui.viewmodel.ViewMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentViewMode: ViewMode,
    currentSortType: SortType,
    currentSortOrder: SortOrder,
    onApply: (ViewMode, SortType, SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedViewMode by remember { mutableStateOf(currentViewMode) }
    var selectedSortType by remember { mutableStateOf(currentSortType) }
    var selectedSortOrder by remember { mutableStateOf(currentSortOrder) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 26.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.display_filter),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // View Mode
        FilterLabel(stringResource(R.string.view_mode))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewModeCard(
                title = "List",
                icon = Icons.AutoMirrored.Filled.List,
                selected = selectedViewMode == ViewMode.LIST,
                onClick = { selectedViewMode = ViewMode.LIST },
                modifier = Modifier.weight(1f)
            )
            ViewModeCard(
                title = "Grid",
                icon = Icons.Default.GridView,
                selected = selectedViewMode == ViewMode.GRID,
                onClick = { selectedViewMode = ViewMode.GRID },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sort By
        FilterLabel(stringResource(R.string.sort_by))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip2(
                label = stringResource(R.string.last_modified),
                selected = selectedSortType == SortType.LAST_MODIFIED,
                onClick = { selectedSortType = SortType.LAST_MODIFIED }
            )
            FilterChip2(
                label = stringResource(R.string.name),
                selected = selectedSortType == SortType.NAME,
                onClick = { selectedSortType = SortType.NAME }
            )
            FilterChip2(
                label = stringResource(R.string.file_size),
                selected = selectedSortType == SortType.FILE_SIZE,
                onClick = { selectedSortType = SortType.FILE_SIZE }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Order
        FilterLabel(stringResource(R.string.order))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip2(
                label = stringResource(R.string.new_to_old),
                selected = selectedSortOrder == SortOrder.NEW_TO_OLD,
                onClick = { selectedSortOrder = SortOrder.NEW_TO_OLD }
            )
            FilterChip2(
                label = stringResource(R.string.old_to_new),
                selected = selectedSortOrder == SortOrder.OLD_TO_NEW,
                onClick = { selectedSortOrder = SortOrder.OLD_TO_NEW }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    stringResource(R.string.cancel),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onApply(selectedViewMode, selectedSortType, selectedSortOrder) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.apply), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun ViewModeCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val unselectedBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val selectedBorder = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedBg else unselectedBg,
        border = if (selected)
            androidx.compose.foundation.BorderStroke(1.5.dp, selectedBorder)
        else
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FilterChip2(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}
