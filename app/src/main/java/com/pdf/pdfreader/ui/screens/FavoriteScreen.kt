package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.ui.components.HomeContent
import com.pdf.pdfreader.ui.viewmodel.FavoriteViewModel
import com.pdf.pdfreader.ui.viewmodel.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    paddingValues: PaddingValues,
    viewModel: FavoriteViewModel = hiltViewModel(),
    onNavigateToReader: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorite PDFs") },
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
            files = uiState.favoriteFiles,
            searchResults = emptyList(),
            searchQuery = "",
            viewMode = ViewMode.LIST,
            isSearching = false,
            activeTab = "Favorites",
            thumbnailManager = viewModel.thumbnailManager,
            onSearchQueryChange = {},
            onTabSelected = {},
            onFilterClick = {},
            onRefresh = { },
            onPdfClick = { onNavigateToReader(it.path) },
            onSearchResultClick = { },
            onRename = { pdf, newName -> viewModel.renamePdf(pdf, newName) },
            onDuplicate = { viewModel.duplicatePdf(it) },
            onShare = { /* Handle */ },
            onFavorite = { viewModel.toggleFavorite(it) },
            onDeleteConfirm = { viewModel.deletePdf(it) }
        )
    }
}
