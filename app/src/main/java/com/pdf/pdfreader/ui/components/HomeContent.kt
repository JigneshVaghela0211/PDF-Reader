package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.R
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.ui.viewmodel.ViewMode
import com.pdf.pdfreader.utiles.ThumbnailManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    paddingValues: PaddingValues,
    isLoading: Boolean,
    isRefreshing: Boolean,
    files: List<PdfFile>,
    searchResults: List<com.pdf.pdfreader.data.local.SearchResult>,
    searchQuery: String,
    viewMode: ViewMode,
    isSearching: Boolean,
    activeTab: String,
    thumbnailManager: ThumbnailManager,
    onSearchQueryChange: (String) -> Unit,
    onTabSelected: (String) -> Unit,
    onFilterClick: () -> Unit,
    onRefresh: () -> Unit,
    onPdfClick: (PdfFile) -> Unit,
    onSearchResultClick: (com.pdf.pdfreader.data.local.SearchResult) -> Unit,
    onRename: (PdfFile, String) -> Unit,
    onDuplicate: (PdfFile) -> Unit,
    onShare: (PdfFile) -> Unit,
    onFavorite: (PdfFile) -> Unit,
    onDeleteConfirm: (PdfFile) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showRenameDialog by remember { mutableStateOf<PdfFile?>(null) }
    var showDeleteDialog by remember { mutableStateOf<PdfFile?>(null) }
    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    val selectedPdf = remember(selectedPdfPath, files) {
        files.find { it.path == selectedPdfPath }
    }

    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

    if (selectedPdf != null) {
        val pdf = selectedPdf
        ModalBottomSheet(
            onDismissRequest = { selectedPdfPath = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            PdfActionsBottomSheet(
                pdf = pdf,
                thumbnailManager = thumbnailManager,
                onRename = {
                    showRenameDialog = pdf
                    // We don't null selectedPdfPath immediately to keep composition stable 
                    // until dialog is confirmed/dismissed if needed, but ModalBottomSheet 
                    // should dismiss.
                    selectedPdfPath = null
                },
                onDuplicate = {
                    onDuplicate(pdf)
                    selectedPdfPath = null
                },
                onShare = {
                    onShare(pdf)
                    selectedPdfPath = null
                },
                onFavorite = { onFavorite(pdf) },
                onDelete = {
                    showDeleteDialog = pdf
                    selectedPdfPath = null
                },
                onDismiss = { selectedPdfPath = null }
            )
        }
    }

    SmartSwipeRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        headerColor = MaterialTheme.colorScheme.primary
    ) {
        val maxSpan = if (viewMode == ViewMode.GRID) 2 else 1
        
        LazyVerticalGrid(
            columns = if (viewMode == ViewMode.GRID) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Content
            item(span = { GridItemSpan(maxSpan) }) {
                Column {
                    // Greeting
                    Text("Good morning, Alex", style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onSurface)
                    Text("You have ${files.size} new documents curated today.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Unified Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search your library...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(
                                onClick = { 
                                    onSearchQueryChange("Voice search coming soon...") 
                                }, 
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onFilterClick, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = "Filter", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Dynamic Tabs
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf("Home", "Recent", "Favorites", "Shared")
                        tabs.forEach { tab ->
                            val isActive = activeTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { onTabSelected(tab) }
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Grid Content
            if (isLoading && !isRefreshing) {
                item(span = { GridItemSpan(maxSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                if (isSearching) {
                    // Search layout inside grid
                    item(span = { GridItemSpan(maxSpan) }) {
                        Text("Search Results", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    items(
                        items = searchResults,
                        key = { "${it.pdfPath}_${it.pageIndex}_${it.snippet}" },
                        span = { GridItemSpan(maxSpan) }
                    ) { result ->
                        SearchResultItem(result = result, onClick = onSearchResultClick)
                    }
                } else if (files.isEmpty()) {
                    item(span = { GridItemSpan(maxSpan) }) {
                        EmptyPdfContent(modifier = Modifier.fillMaxWidth().height(300.dp), isSearching = isSearching)
                    }
                } else {
                    // Responsive Grid/List logic
                    files.forEachIndexed { index, pdf ->
                        val isHero = index == 0 && viewMode == ViewMode.GRID
                        item(
                            span = { GridItemSpan(if (isHero || viewMode == ViewMode.LIST) maxSpan else 1) },
                            key = pdf.path
                        ) {
                            if (viewMode == ViewMode.GRID) {
                                if (isHero) {
                                    BentoHeroItem(pdf = pdf, thumbnailManager = thumbnailManager, onClick = onPdfClick, onMoreClick = { selectedPdfPath = it.path })
                                } else {
                                    BentoStandardItem(pdf = pdf, thumbnailManager = thumbnailManager, onClick = onPdfClick, onMoreClick = { selectedPdfPath = it.path })
                                }
                            } else {
                                PremiumListItem(pdf = pdf, thumbnailManager = thumbnailManager, onClick = onPdfClick, onMoreClick = { selectedPdfPath = it.path })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog != null) {
        val pdf = showRenameDialog!!
        var newName by remember { mutableStateOf(pdf.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onRename(pdf, newName)
                    showRenameDialog = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteDialog != null) {
        val pdf = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete PDF") },
            text = { Text("Are you sure you want to permanently delete '${pdf.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConfirm(pdf)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun BentoHeroItem(
    pdf: PdfFile,
    thumbnailManager: ThumbnailManager,
    onClick: (PdfFile) -> Unit,
    onMoreClick: (PdfFile) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),    
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick(pdf) }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PdfThumbnail(pdf = pdf, thumbnailManager = thumbnailManager, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Primary Asset",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = pdf.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(pdf.formattedSize, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(pdf.formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = { onMoreClick(pdf) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun BentoStandardItem(
    pdf: PdfFile,
    thumbnailManager: ThumbnailManager,
    onClick: (PdfFile) -> Unit,
    onMoreClick: (PdfFile) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick(pdf) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f/3f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                PdfThumbnail(pdf = pdf, thumbnailManager = thumbnailManager, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = pdf.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp).clickable { onMoreClick(pdf) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(pdf.formattedDate, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.outline)
                }
                Text(pdf.formattedSize, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
@Composable
fun PremiumListItem(
    pdf: PdfFile,
    thumbnailManager: ThumbnailManager,
    onClick: (PdfFile) -> Unit,
    onMoreClick: (PdfFile) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(pdf) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                PdfThumbnail(pdf = pdf, thumbnailManager = thumbnailManager, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pdf.formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.padding(horizontal = 6.dp).size(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                    Text(pdf.formattedSize, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onMoreClick(pdf) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyPdfContent(
    modifier: Modifier = Modifier,
    isSearching: Boolean = false
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearching) stringResource(R.string.no_results_found) else stringResource(R.string.no_pdf_found),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isSearching) {
            Text(
                text = stringResource(R.string.adjust_search),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    onGrantClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.storage_access_required),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.storage_permission_desc),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.go_to_settings))
        }
    }
}

@Composable
fun SearchResultItem(
    result: com.pdf.pdfreader.data.local.SearchResult,
    onClick: (com.pdf.pdfreader.data.local.SearchResult) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        onClick = { onClick(result) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = result.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.page) + " " + (result.pageIndex + 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            val cleanSnippet = result.snippet.replace("<b>", "").replace("</b>", "")
            Text(text = cleanSnippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
