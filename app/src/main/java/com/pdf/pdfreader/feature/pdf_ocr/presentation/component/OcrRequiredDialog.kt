package com.pdf.pdfreader.feature.pdf_ocr.presentation.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Shown when the user taps Edit Text on an image-based (scanned) document:
 * real text editing has nothing to edit, so OCR must run first.
 *
 * [onRunWholeDocumentOcr] adds a batch-OCR action (whole document → searchable
 * `_ocr.pdf`); pass null when ENABLE_BATCH_OCR is off.
 */
@Composable
fun OcrRequiredDialog(
    onRunOcr: () -> Unit,
    onDismiss: () -> Unit,
    onRunWholeDocumentOcr: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
        title = { Text("OCR required") },
        text = { Text("This document is image-based.\n\nOCR is required before editing.") },
        confirmButton = {
            TextButton(onClick = onRunOcr) { Text("Run OCR & Edit") }
        },
        dismissButton = {
            Row {
                if (onRunWholeDocumentOcr != null) {
                    TextButton(onClick = onRunWholeDocumentOcr) { Text("OCR whole document") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
