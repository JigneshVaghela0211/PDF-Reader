package com.pdf.pdfreader.feature.annotation.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.domain.model.PdfAnnotation

/**
 * Lists every annotation in the document (drawings, markups, notes), sorted by page.
 * Tap a row to jump to its page; the trailing icon deletes it (undoable via the
 * existing [com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel.removeAnnotation]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationListSheet(
    annotations: List<PdfAnnotation>,
    onJumpToPage: (pageIndex: Int) -> Unit,
    onDelete: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Annotations" + if (annotations.isNotEmpty()) " (${annotations.size})" else "",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (annotations.isEmpty()) {
                EmptyAnnotations()
            } else {
                val sorted = annotations.sortedBy { it.pageIndex }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(sorted, key = { it.id }) { annotation ->
                        AnnotationRow(
                            annotation = annotation,
                            onClick = { onJumpToPage(annotation.pageIndex); onDismiss() },
                            onDelete = { onDelete(annotation.id) }
                        )
                    }
                }
            }
        }
    }
}

private data class AnnotationDisplay(val icon: ImageVector, val title: String, val color: Color)

private fun PdfAnnotation.toDisplay(): AnnotationDisplay = when (this) {
    is PdfAnnotation.Path -> AnnotationDisplay(
        icon = Icons.Default.Brush,
        title = if (isHighlighter) "Marker" else "Drawing",
        color = color
    )
    is PdfAnnotation.TextNote -> AnnotationDisplay(
        icon = Icons.Default.Notes,
        title = text.ifBlank { "Note" },
        color = color
    )
    is PdfAnnotation.TextMarkup -> when (type) {
        PdfAnnotation.MarkupType.HIGHLIGHT -> AnnotationDisplay(Icons.Default.Highlight, "Highlight", color)
        PdfAnnotation.MarkupType.UNDERLINE -> AnnotationDisplay(Icons.Default.FormatUnderlined, "Underline", color)
        PdfAnnotation.MarkupType.STRIKETHROUGH -> AnnotationDisplay(Icons.Default.FormatStrikethrough, "Strikethrough", color)
    }
}

@Composable
private fun AnnotationRow(
    annotation: PdfAnnotation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val display = annotation.toDisplay()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(display.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                display.icon,
                contentDescription = null,
                tint = display.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Page ${annotation.pageIndex + 1}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete annotation",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyAnnotations() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("No annotations yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Highlight, draw or add notes to see them here",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
