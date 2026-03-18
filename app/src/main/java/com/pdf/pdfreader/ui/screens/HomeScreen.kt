package com.pdf.pdfreader.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.ui.components.*
import com.pdf.pdfreader.ui.viewmodel.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    true
                }
                if (hasPermission) {
                    viewModel.loadPdfFiles()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopAppBar(
                isSearchExpanded = isSearchExpanded,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onSearchToggle = { isSearchExpanded = it },
                onFilterClick = { showFilterSheet = true },
                onSettingsClick = onNavigateToSettings,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* TODO: Create PDF logic */ },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create PDF") }
            )
        }
    ) { padding ->
        if (!hasPermission) {
            PermissionDeniedContent(
                modifier = Modifier.padding(padding),
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
                paddingValues = padding,
                isLoading = uiState.isLoading,
                isRefreshing = uiState.isRefreshing,
                files = uiState.filteredFiles,
                viewMode = uiState.viewMode,
                isSearching = uiState.searchQuery.isNotEmpty(),
                thumbnailManager = viewModel.thumbnailManager,
                onRefresh = { viewModel.loadPdfFiles(false) },
                onPdfClick = { onNavigateToReader(it.path) },
                onRename = { /* TODO: Rename logic */ },
                onShare = { /* TODO: Share logic */ },
                onFavorite = { /* TODO: Favorite logic */ },
                onDelete = { /* TODO: Delete logic */ }
            )
        }
    }
}
