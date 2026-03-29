package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pdf.pdfreader.domain.model.PdfAnnotation

@Composable
fun MovableTextNote(
    note: PdfAnnotation.TextNote,
    isEditMode: Boolean,
    onUpdate: (PdfAnnotation.TextNote) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember { mutableStateOf(note.text) }
    var offset by remember { mutableStateOf(note.position) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (note.text.isEmpty() && isEditMode) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectDragGestures(
                        onDragEnd = {
                            onUpdate(note.copy(text = text, position = offset))
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            }
            .background(if (isEditMode) Color.White.copy(alpha = 0.8f) else Color.Transparent)
            .border(
                width = if (isEditMode) 1.dp else 0.dp,
                color = if (isEditMode) Color.Gray else Color.Transparent
            )
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onUpdate(note.copy(text = it, position = offset))
                },
                textStyle = TextStyle(
                    color = note.color,
                    fontSize = with(LocalDensity.current) { note.fontSize.toSp() }
                ),
                enabled = isEditMode,
                modifier = Modifier.focusRequester(focusRequester)
            )

            if (isEditMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete Note",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
