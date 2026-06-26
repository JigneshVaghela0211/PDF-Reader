package com.pdf.pdfreader.feature.reader.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Lets the user jump directly to a page by typing its number.
 *
 * The field accepts 1-based page numbers (what the user sees); the [onConfirm]
 * callback receives a 0-based, in-range page index.
 */
@Composable
fun GoToPageDialog(
    totalPages: Int,
    currentPage: Int,
    onConfirm: (pageIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf((currentPage + 1).toString()) }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in 1..totalPages

    fun confirm() {
        if (isValid) {
            onConfirm(parsed!! - 1)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !isValid,
                    label = { Text("Page (1 – $totalPages)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { confirm() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = isValid) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
