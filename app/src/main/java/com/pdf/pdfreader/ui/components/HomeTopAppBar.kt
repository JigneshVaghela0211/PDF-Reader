package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.R
import com.pdf.pdfreader.ui.viewmodel.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    isSearchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewMode: ViewMode,
    fileCount: Int,
    onViewModeChange: (ViewMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (scrollBehavior.state.collapsedFraction > 0f) 4.dp else 0.dp
    ) {
        Column {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "search_toggle"
            ) { expanded ->
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                onSearchToggle(false)
                                onSearchQueryChange("")
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PDF Pro",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.02).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        AppBarAction(onClick = { onSearchToggle(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(21.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        AppBarAction(onClick = onFilterClick) {
                            Icon(Icons.Default.Tune, contentDescription = "Filter", modifier = Modifier.size(21.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        AppBarAction(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }

            if (!isSearchExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$fileCount documents",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.weight(1f)
                    )
                    ViewModeToggle(viewMode = viewMode, onViewModeChange = onViewModeChange)
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun AppBarAction(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(Modifier.clip(CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            content()
        }
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val pillBg = MaterialTheme.colorScheme.surfaceVariant
    val activePillBg = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillBg)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewToggleButton(
            selected = viewMode == ViewMode.GRID,
            background = if (viewMode == ViewMode.GRID) activePillBg else Color.Transparent,
            onClick = { onViewModeChange(ViewMode.GRID) }
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "Grid",
                modifier = Modifier.size(18.dp),
                tint = if (viewMode == ViewMode.GRID) activeColor else inactiveColor
            )
        }
        ViewToggleButton(
            selected = viewMode == ViewMode.LIST,
            background = if (viewMode == ViewMode.LIST) activePillBg else Color.Transparent,
            onClick = { onViewModeChange(ViewMode.LIST) }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "List",
                modifier = Modifier.size(18.dp),
                tint = if (viewMode == ViewMode.LIST) activeColor else inactiveColor
            )
        }
    }
}

@Composable
private fun ViewToggleButton(
    selected: Boolean,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 30.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(
                if (selected) Modifier else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
            content()
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
