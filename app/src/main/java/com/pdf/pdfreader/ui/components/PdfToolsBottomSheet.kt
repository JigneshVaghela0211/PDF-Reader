package com.pdf.pdfreader.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.presentation.editor.ToolbarFeatureProvider
import com.pdf.pdfreader.utiles.PdfCompressionEngine
import com.pdf.pdfreader.ui.viewmodel.PdfToolsViewModel
import com.pdf.pdfreader.ui.viewmodel.ToolStatus

/**
 * Bottom-sheet body listing the PDF Tools (Merge / Split / Compress) for a single file. Each tool
 * is shown only when [PdfEditorFeatureConfig] marks it visible, and is interactive only when the
 * config marks it interactive (BETA gets a badge) — no hardcoded on/off logic here.
 *
 * Self-contained: it owns its own [PdfToolsViewModel] and result handling, so the host only needs
 * to render it inside a ModalBottomSheet.
 */
@Composable
fun PdfToolsBottomSheet(
    pdf: PdfFile,
    onDismiss: () -> Unit
) {
    val vm: PdfToolsViewModel = hiltViewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCompress by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }

    val mergeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) vm.merge(pdf.path, uris)
    }

    // Surface success/error as a toast, then reset so it doesn't re-fire on recomposition.
    LaunchedEffect(status) {
        when (val s = status) {
            is ToolStatus.Success -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                vm.resetStatus()
                onDismiss()
            }
            is ToolStatus.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                vm.resetStatus()
            }
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Text(
            text = "PDF Tools",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        )
        Spacer(Modifier.height(6.dp))

        val running = status is ToolStatus.Running

        ToolRow(EditorFeature.COMPRESS, Icons.Default.Compress, "Compress", "Reduce file size", running) { showCompress = true }
        ToolRow(EditorFeature.SPLIT, Icons.Default.ContentCut, "Split", "Export pages or ranges", running) { showSplit = true }
        ToolRow(EditorFeature.MERGE, Icons.Default.MergeType, "Merge", "Combine with other PDFs", running) {
            mergeLauncher.launch(arrayOf("application/pdf"))
        }
        ToolRow(EditorFeature.OCR, Icons.Default.DocumentScanner, "Make Searchable (OCR)", "Recognize text in scanned PDFs", running) {
            vm.runOcr(pdf.path)
        }

        if (running) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text((status as ToolStatus.Running).label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCompress) {
        CompressLevelDialog(
            onDismiss = { showCompress = false },
            onPick = { level -> showCompress = false; vm.compress(pdf.path, level) }
        )
    }
    if (showSplit) {
        SplitOptionsDialog(
            onDismiss = { showSplit = false },
            onEveryPage = { showSplit = false; vm.splitEveryPage(pdf.path) },
            onRanges = { ranges -> showSplit = false; vm.splitRanges(pdf.path, ranges) }
        )
    }
}

@Composable
private fun ToolRow(
    feature: EditorFeature,
    icon: ImageVector,
    label: String,
    subtitle: String,
    disabled: Boolean,
    onClick: () -> Unit
) {
    val model = ToolbarFeatureProvider.uiModel(feature)
    if (!model.visible) return
    val interactive = model.interactive && !disabled
    val contentColor = if (interactive) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp),
                tint = if (interactive) MaterialTheme.colorScheme.primary else contentColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = contentColor)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        FeatureBadgeDecoration(model.badge)
    }
}

@Composable
private fun CompressLevelDialog(
    onDismiss: () -> Unit,
    onPick: (PdfCompressionEngine.Level) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Compress PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                Text(
                    "Higher compression makes a smaller file with lower image quality.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LevelItem("Low", "Best quality, modest savings") { onPick(PdfCompressionEngine.Level.LOW) }
                LevelItem("Medium", "Balanced") { onPick(PdfCompressionEngine.Level.MEDIUM) }
                LevelItem("High", "Smallest file") { onPick(PdfCompressionEngine.Level.HIGH) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LevelItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SplitOptionsDialog(
    onDismiss: () -> Unit,
    onEveryPage: () -> Unit,
    onRanges: (List<IntRange>) -> Unit
) {
    var rangeText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Split PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                LevelItem("Every page", "One PDF per page", onEveryPage)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Page ranges", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                Text("e.g. 1-10, 11-20", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rangeText,
                    onValueChange = { rangeText = it },
                    singleLine = true,
                    placeholder = { Text("1-10, 11-20") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rangeText.isNotBlank(),
                onClick = { onRanges(PdfToolsViewModel.parseRanges(rangeText)) }
            ) { Text("Split ranges", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
