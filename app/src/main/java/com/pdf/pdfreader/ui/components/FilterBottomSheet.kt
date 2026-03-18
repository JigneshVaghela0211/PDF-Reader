package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.display_filter),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // View Mode Section
        FilterSectionTitle(stringResource(R.string.view_mode))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        Spacer(modifier = Modifier.height(24.dp))

        // Sort By Section
        FilterSectionTitle(stringResource(R.string.sort_by))
        FlowRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOptionChip(
                label = stringResource(R.string.last_modified),
                selected = selectedSortType == SortType.LAST_MODIFIED,
                onClick = { selectedSortType = SortType.LAST_MODIFIED }
            )
            SortOptionChip(
                label = stringResource(R.string.name),
                selected = selectedSortType == SortType.NAME,
                onClick = { selectedSortType = SortType.NAME }
            )
            SortOptionChip(
                label = stringResource(R.string.file_size),
                selected = selectedSortType == SortType.FILE_SIZE,
                onClick = { selectedSortType = SortType.FILE_SIZE }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Order Section
        FilterSectionTitle(stringResource(R.string.order))
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOptionChip(
                label = stringResource(R.string.new_to_old),
                selected = selectedSortOrder == SortOrder.NEW_TO_OLD,
                onClick = { selectedSortOrder = SortOrder.NEW_TO_OLD },
                modifier = Modifier.weight(1f)
            )
            SortOptionChip(
                label = stringResource(R.string.old_to_new),
                selected = selectedSortOrder == SortOrder.OLD_TO_NEW,
                onClick = { selectedSortOrder = SortOrder.OLD_TO_NEW },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(52.dp).weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onApply(selectedViewMode, selectedSortType, selectedSortOrder) },
                modifier = Modifier.height(52.dp).weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(stringResource(R.string.apply), fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
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
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SortOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
