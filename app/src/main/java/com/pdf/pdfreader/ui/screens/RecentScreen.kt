package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.ui.components.HomeContent
import com.pdf.pdfreader.ui.viewmodel.RecentViewModel
import com.pdf.pdfreader.ui.viewmodel.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    paddingValues: PaddingValues,
    viewModel: RecentViewModel = hiltViewModel(),
    onNavigateToReader: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ListHeader(
                        title = "Recent",
                        subtitle = "Recently opened documents"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        HomeContent(
            paddingValues = innerPadding,
            isLoading = uiState.isLoading,
            isRefreshing = false,
            files = uiState.recentFiles,
            searchResults = emptyList(),
            viewMode = ViewMode.LIST,
            isSearching = false,
            thumbnailManager = viewModel.thumbnailManager,
            onRefresh = { },
            onPdfClick = {
                viewModel.markAsOpened(it.path)
                onNavigateToReader(it.path)
            },
            onSearchResultClick = { },
            onRename = { pdf, newName -> viewModel.renamePdf(pdf, newName) },
            onDuplicate = { viewModel.duplicatePdf(it) },
            onShare = { /* Handle */ },
            onFavorite = { /* Handle Favorite */ },
            onDeleteConfirm = { viewModel.deletePdf(it) }
        )
    }
}

@Composable
fun ListHeader(title: String, subtitle: String) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            letterSpacing = (-0.02).sp
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
