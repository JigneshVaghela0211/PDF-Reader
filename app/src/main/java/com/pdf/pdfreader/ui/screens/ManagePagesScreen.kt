package com.pdf.pdfreader.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.ui.viewmodel.ManagePagesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePagesScreen(
    viewModel: ManagePagesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Width for thumbnails based on grid logic: 3 columns with padding
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    val density = LocalDensity.current.density
    val thumbWidthPx = (screenWidth / 3f).toInt()
    
    BackHandler {
        if (uiState.selectedPages.isNotEmpty()) {
            viewModel.clearSelection()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (uiState.selectedPages.isNotEmpty()) {
                        Text("${uiState.selectedPages.size} Selected")
                    } else {
                        Text("Manage Pages")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            if (uiState.selectedPages.isNotEmpty()) {
                                viewModel.clearSelection()
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        val icon = if (uiState.selectedPages.isNotEmpty()) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowBack
                        Icon(icon, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        bottomBar = {
            if (uiState.selectedPages.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionItem(
                            icon = Icons.Default.RotateRight,
                            label = "Rotate",
                            onClick = {
                                viewModel.rotateSelectedPages(90) { success ->
                                    if (success) Toast.makeText(context, "Pages rotated", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        ActionItem(
                            icon = Icons.Default.FileDownload,
                            label = "Extract",
                            onClick = {
                                val downloadsStr = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                                viewModel.extractSelectedPages(downloadsStr) { path ->
                                    if (path != null) {
                                        Toast.makeText(context, "Extracted to Downloads", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                        ActionItem(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.deleteSelectedPages { success ->
                                    if (success) Toast.makeText(context, "Pages deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.totalPages > 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.totalPages, key = { it }) { pageIndex ->
                        var thumb by remember { mutableStateOf<Bitmap?>(null) }
                        
                        LaunchedEffect(pageIndex, uiState.reloadTrigger) {
                            thumb = viewModel.getThumbnail(pageIndex, thumbWidthPx)
                        }
                        
                        val isSelected = uiState.selectedPages.contains(pageIndex)
                        
                        PageThumbnail(
                            bitmap = thumb,
                            pageIndex = pageIndex,
                            isSelected = isSelected,
                            onClick = { viewModel.toggleSelection(pageIndex) }
                        )
                    }
                }
            } else {
                Text("No pages available")
            }
            
            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun PageThumbnail(
    bitmap: Bitmap?,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}
