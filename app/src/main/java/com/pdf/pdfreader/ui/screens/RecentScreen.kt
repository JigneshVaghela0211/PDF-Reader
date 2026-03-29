package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                title = { Text("Recent PDFs") },
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
            onRename = { /* Handle */ },
            onShare = { /* Handle */ },
            onFavorite = { /* Handle Favorite */ },
            onDelete = { /* Handle */ }
        )
    }
}
