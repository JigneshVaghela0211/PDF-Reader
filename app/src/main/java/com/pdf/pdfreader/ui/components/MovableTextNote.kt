package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.domain.model.PdfAnnotation

/**
 * Movable text annotation with inline style controls:
 * - Color picker (dots)
 * - Font size +/- buttons
 * - Drag to reposition
 * - Delete button
 */
@Composable
fun MovableTextNote(
    note: PdfAnnotation.TextNote,
    isEditMode: Boolean,
    onUpdate: (PdfAnnotation.TextNote) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    var offset by remember(note.id) { mutableStateOf(note.position) }
    var color by remember(note.id) { mutableStateOf(note.color) }
    var fontSize by remember(note.id) { mutableStateOf(note.fontSize) }
    val focusRequester = remember { FocusRequester() }

    // Available colors for quick-pick
    val colorOptions = remember {
        listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color(0xFFFF9800), Color(0xFF9C27B0))
    }

    LaunchedEffect(note.id) {
        if (note.text.isEmpty() && isEditMode) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectDragGestures(
                        onDragEnd = {
                            onUpdate(note.copy(text = text, position = offset, color = color, fontSize = fontSize))
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            }
    ) {
        Column {
            // Text field with styling
            Box(
                modifier = Modifier
                    .background(
                        if (isEditMode) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .then(
                        if (isEditMode) Modifier
                            .shadow(2.dp, RoundedCornerShape(8.dp))
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onUpdate(note.copy(text = it, position = offset, color = color, fontSize = fontSize))
                        },
                        textStyle = TextStyle(
                            color = color,
                            fontSize = with(LocalDensity.current) { fontSize.toSp() },
                            fontWeight = FontWeight.Normal
                        ),
                        enabled = isEditMode,
                        modifier = Modifier.focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty() && isEditMode) {
                                    Text(
                                        text = "Type here…",
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        fontSize = with(LocalDensity.current) { fontSize.toSp() }
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (isEditMode) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Note",
                                tint = Color.Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Inline style controls (only visible in edit mode)
            if (isEditMode) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.92f),
                            RoundedCornerShape(16.dp)
                        )
                        .shadow(1.dp, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Color picker dots
                    colorOptions.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (c == color) Modifier.border(2.dp, Color.DarkGray, CircleShape)
                                    else Modifier.border(0.5.dp, Color.LightGray, CircleShape)
                                )
                                .clickable {
                                    color = c
                                    onUpdate(note.copy(text = text, position = offset, color = c, fontSize = fontSize))
                                }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Font size controls
                    IconButton(
                        onClick = {
                            val newSize = (fontSize - 2f).coerceAtLeast(10f)
                            fontSize = newSize
                            onUpdate(note.copy(text = text, position = offset, color = color, fontSize = newSize))
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease font",
                            modifier = Modifier.size(14.dp),
                            tint = Color.DarkGray
                        )
                    }

                    Text(
                        text = "${fontSize.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    IconButton(
                        onClick = {
                            val newSize = (fontSize + 2f).coerceAtMost(72f)
                            fontSize = newSize
                            onUpdate(note.copy(text = text, position = offset, color = color, fontSize = newSize))
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase font",
                            modifier = Modifier.size(14.dp),
                            tint = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}
