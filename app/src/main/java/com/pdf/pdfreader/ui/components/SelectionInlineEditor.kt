package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Max characters for a single selection edit; drives the counter and caps input. */
private const val MAX_EDIT_CHARS = 200

/**
 * Micro Chunk 3 (+ UX polish): inline editor for the current text selection.
 *
 * Shows the selected text as the ONLY editable field, framed by dimmed read-only context
 * ([previousContext] / [nextContext]) so the user sees what they're editing in place. Edits its own
 * draft only (UI state) and returns the confirmed text via [onConfirm]; it performs NO PDF
 * replacement. [onCancel] closes the editor with the selection left active.
 */
@Composable
fun SelectionInlineEditor(
    initialText: String,
    isMultiline: Boolean,
    wordCount: Int,
    previousContext: String?,
    nextContext: String?,
    offsetX: Int,
    offsetY: Int,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    // TextFieldValue so we can place the cursor at the end on open.
    var draft by remember(initialText) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    // Keep focus: auto-open the keyboard on the field.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val contextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Box(modifier = Modifier.offset { IntOffset(offsetX, offsetY) }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .width(if (isMultiline) 300.dp else 240.dp)
            ) {
                // Title + selection info.
                Text(
                    text = "Edit Selected Text",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Editing $wordCount ${if (wordCount == 1) "Word" else "Words"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                // Read-only previous context.
                if (!previousContext.isNullOrBlank()) {
                    Text(
                        text = previousContext,
                        color = contextColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                // The ONLY editable text, visually highlighted.
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.text.length <= MAX_EDIT_CHARS) draft = it },
                    singleLine = !isMultiline,
                    minLines = if (isMultiline) 3 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = highlight,
                        unfocusedContainerColor = highlight
                    )
                )

                // Read-only next context.
                if (!nextContext.isNullOrBlank()) {
                    Text(
                        text = nextContext,
                        color = contextColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Character counter.
                Text(
                    text = "${draft.text.length} / $MAX_EDIT_CHARS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onConfirm(draft.text) }) { Text("Confirm") }
                }
            }
        }
    }
}
