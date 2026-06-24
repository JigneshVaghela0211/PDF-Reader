package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    viewMode: ViewMode,
    isSearching: Boolean,
    thumbnailManager: ThumbnailManager,
    onRefresh: () -> Unit,
    onPdfClick: (PdfFile) -> Unit,
    onSearchResultClick: (com.pdf.pdfreader.data.local.SearchResult) -> Unit,
    onRename: (PdfFile, String) -> Unit,
    onDuplicate: (PdfFile) -> Unit,
    onShare: (PdfFile) -> Unit,
    onFavorite: (PdfFile) -> Unit,
    onDeleteConfirm: (PdfFile) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf<PdfFile?>(null) }
    var showDeleteDialog by remember { mutableStateOf<PdfFile?>(null) }
    var selectedPdfPath by remember { mutableStateOf<String?>(null) }
    val selectedPdf = remember(selectedPdfPath, files) { 
        files.find { it.path == selectedPdfPath } 
    }
    
    val sheetState = rememberModalBottomSheetState()
    
    if (selectedPdf != null) {
        val pdf = selectedPdf
        ModalBottomSheet(
            onDismissRequest = { selectedPdfPath = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            PdfActionsBottomSheet(
                pdf = pdf,
                thumbnailManager = thumbnailManager,
                onRename = { 
                    showRenameDialog = pdf
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
        if (isLoading && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        } else if (files.isEmpty() && searchResults.isEmpty()) {
            EmptyPdfContent(
                modifier = Modifier.fillMaxSize(),
                isSearching = isSearching
            )
        } else {
            if (viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (files.isNotEmpty()) {
                        if (isSearching) {
                            item {
                                Text(
                                    text = stringResource(R.string.file_matches),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(
                            items = files,
                            key = { it.path },
                            contentType = { "pdf_item" }
                        ) { pdf ->
                            PdfItem(
                                pdf = pdf,
                                thumbnailManager = thumbnailManager,
                                onClick = onPdfClick,
                                onMoreClick = { selectedPdfPath = it.path }
                            )
                        }
                    }

                    if (isSearching && searchResults.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.text_matches),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(
                            items = searchResults,
                            key = { "${it.pdfPath}_${it.pageIndex}_${it.snippet}" },
                            contentType = { "search_result" }
                        ) { result ->
                            SearchResultItem(
                                result = result,
                                onClick = onSearchResultClick
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = files,
                        key = { it.path },
                        contentType = { "pdf_grid_item" }
                    ) { pdf ->
                        // Assuming PdfGridItem will also be updated later or if it has its own logic
                        PdfGridItem(
                            pdf = pdf,
                            thumbnailManager = thumbnailManager,
                            onClick = onPdfClick
                        )
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
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Rename", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    suffix = { Text(".pdf", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onRename(pdf, newName)
                    showRenameDialog = null
                }) {
                    Text("Rename", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showDeleteDialog != null) {
        val pdf = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            shape = RoundedCornerShape(24.dp),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Delete file?", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "\"${pdf.name}\" will be permanently removed.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = null },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                onDeleteConfirm(pdf)
                                showDeleteDialog = null
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
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
fun SearchResultItem(
    result: com.pdf.pdfreader.data.local.SearchResult,
    onClick: (com.pdf.pdfreader.data.local.SearchResult) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        onClick = { onClick(result) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.page) + " " + (result.pageIndex + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            val cleanSnippet = result.snippet.replace("<b>", "").replace("</b>", "")
            Text(
                text = cleanSnippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
