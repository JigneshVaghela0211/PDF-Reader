package com.pdf.pdfreader.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.ui.components.*
import com.pdf.pdfreader.ui.viewmodel.PdfViewModel
import com.pdf.pdfreader.ui.viewmodel.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToReaderWithSearch: (String, Int, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial load logic
    LaunchedEffect(hasPermission) {
        if (hasPermission && uiState.pdfFiles.isEmpty()) {
            viewModel.loadPdfFiles(isInitialLoad = true)
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            FilterBottomSheet(
                currentViewMode = uiState.viewMode,
                currentSortType = uiState.sortType,
                currentSortOrder = uiState.sortOrder,
                onApply = { viewMode, sortType, sortOrder ->
                    viewModel.onViewModeChange(viewMode)
                    viewModel.updateSortSettings(sortType, sortOrder)
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }

    Scaffold(
        modifier = Modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeTopAppBar(
                onMenuClick = onNavigateToSettings,
                onProfileClick = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    scope.launch {
                        snackbarHostState.showSnackbar("Create PDF feature coming soon!")
                    }
                },
                containerColor = Color.Transparent, 
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.padding(bottom = 80.dp) // Offset above bottom navbar
            ) {
                Box(modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF004591), Color(0xFF005CBD))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create PDF", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                PermissionDeniedContent(
                    modifier = Modifier,
                    onGrantClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                            context.startActivity(intent)
                        }
                    }
                )
            } else {
                HomeContent(
                    paddingValues = PaddingValues(0.dp), // Padding already handled by Box.padding(padding)
                    isLoading = uiState.isLoading,
                    isRefreshing = uiState.isRefreshing,
                    files = uiState.filteredFiles,
                    searchResults = uiState.searchResults,
                    searchQuery = uiState.searchQuery,
                    activeTab = uiState.activeTab,
                    viewMode = uiState.viewMode,
                    isSearching = uiState.searchQuery.isNotEmpty(),
                    thumbnailManager = viewModel.thumbnailManager,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onTabSelected = viewModel::onTabSelected,
                    onFilterClick = { showFilterSheet = true },
                    onRefresh = { viewModel.loadPdfFiles(false) },
                    onPdfClick = { viewModel.onPdfClick(it, onNavigateToReader) },
                    onSearchResultClick = { result ->
                        onNavigateToReaderWithSearch(result.pdfPath, result.pageIndex, uiState.searchQuery)
                    },
                    onRename = { pdf, newName -> viewModel.renamePdf(pdf, newName) },
                    onDuplicate = { viewModel.duplicatePdf(it) },
                    onShare = { /* TODO: Share logic */ },
                    onFavorite = { viewModel.toggleFavorite(it) },
                    onDeleteConfirm = { viewModel.deletePdf(it) }
                )
            }
        }
    }
}
