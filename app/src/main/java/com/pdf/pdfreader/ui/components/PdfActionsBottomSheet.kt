package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.utiles.ThumbnailManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfActionsBottomSheet(
    pdf: PdfFile,
    thumbnailManager: ThumbnailManager,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onTools: () -> Unit,
    onDismiss: () -> Unit
) {
    // Show the PDF Tools entry only when at least one tool is visible per the feature config.
    val toolsVisible = PdfEditorFeatureConfig.isVisible(EditorFeature.MERGE) ||
        PdfEditorFeatureConfig.isVisible(EditorFeature.SPLIT) ||
        PdfEditorFeatureConfig.isVisible(EditorFeature.COMPRESS) ||
        PdfEditorFeatureConfig.isVisible(EditorFeature.OCR)
    var showInfoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp)
    ) {
        // Header: thumbnail + file info + info button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.size(width = 48.dp, height = 60.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.TopCenter)
                        )
                        PdfThumbnail(
                            pdf = pdf,
                            thumbnailManager = thumbnailManager,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${pdf.formattedSize} · ${pdf.formattedDate}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showInfoDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "File Info",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Action rows (vertical list)
        ActionRow(
            icon = Icons.Outlined.Edit,
            label = "Rename",
            onClick = onRename
        )
        ActionRow(
            icon = Icons.Outlined.ContentCopy,
            label = "Duplicate",
            onClick = onDuplicate
        )
        ActionRow(
            icon = Icons.Outlined.Share,
            label = "Share",
            onClick = onShare
        )
        if (toolsVisible) {
            ActionRow(
                icon = Icons.Outlined.Build,
                label = "PDF Tools",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onTools
            )
        }
        ActionRow(
            icon = if (pdf.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            label = if (pdf.isFavorite) "Remove from Favorites" else "Add to Favorites",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onFavorite
        )
        ActionRow(
            icon = Icons.Outlined.Delete,
            label = "Delete",
            tint = MaterialTheme.colorScheme.error,
            labelColor = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }

    if (showInfoDialog) {
        FileInfoDialog(pdf = pdf, onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = labelColor)
    }
}

@Composable
fun FileInfoDialog(
    pdf: PdfFile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "File Details",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoRow("PATH", pdf.path)
                InfoRow("MODIFIED", pdf.formattedDate)
                InfoRow("SIZE", pdf.formattedSize)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "OK",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Keep ActionItem/ActionButton for backwards-compat if referenced elsewhere
data class ActionItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tint: Color? = null
)

@Composable
fun ActionButton(action: ActionItem) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { action.onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}
