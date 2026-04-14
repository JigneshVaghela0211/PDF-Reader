package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureBottomSheet(
    visible: Boolean,
    savedSignatures: List<String>,
    onDismissRequest: () -> Unit,
    onCreateNewSignature: () -> Unit,
    onSelectSignature: (String) -> Unit,
    onDeleteSignature: (String) -> Unit
) {
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Signatures",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (savedSignatures.isEmpty()) {
                    Text(
                        "No saved signatures yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(savedSignatures) { uri ->
                            SignatureGridItem(
                                uri = uri,
                                onClick = { onSelectSignature(uri) },
                                onDelete = { onDeleteSignature(uri) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCreateNewSignature,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create New")
                    Spacer(Modifier.width(8.dp))
                    Text("Create Signature")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SignatureGridItem(
    uri: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        val bmp = remember(uri) {
            val path = uri.replace("file://", "")
            BitmapFactory.decodeFile(path)
        }
        
        // Recycle bitmap when composable leaves composition
        androidx.compose.runtime.DisposableEffect(bmp) {
            onDispose {
                if (bmp != null && !bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }
        
        bmp?.let { 
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Saved Signature",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp)
                .padding(4.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
