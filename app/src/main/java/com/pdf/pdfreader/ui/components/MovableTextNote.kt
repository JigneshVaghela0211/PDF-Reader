package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.domain.model.PdfAnnotation

import com.pdf.pdfreader.domain.model.AnnotationCommand

@Composable
fun MovableTextNote(
    note: PdfAnnotation.TextNote,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onCommit: (AnnotationCommand.TextState?, AnnotationCommand.TextState) -> Unit,
    onDelete: () -> Unit
) {
    // True source of truth from ViewModel
    val textToDisplay = note.text
    val color = note.color
    val fontSize = note.fontSize
    var currentOffset by remember(note.id) { mutableStateOf(note.position) }

    // Transient typing state
    var isTyping by remember { mutableStateOf(note.text.isEmpty()) }
    var transientText by remember { mutableStateOf(note.text) }

    // Store the state right before an editing session begins
    var beforeState by remember { mutableStateOf<AnnotationCommand.TextState?>(null) }
    
    val currentState = remember(note) {
        AnnotationCommand.TextState(note.id, note.text, note.color.value.toLong(), note.fontSize, note.position.x, note.position.y)
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keep transient text in sync with global text if external changes happen (like Undo/Redo) while not typing
    LaunchedEffect(note.text) {
        if (!isTyping) {
            transientText = note.text
        }
    }

    LaunchedEffect(isTyping) {
        if (isTyping) {
            beforeState = if (note.text.isEmpty()) null else currentState
            try { focusRequester.requestFocus() } catch (e: Exception) {}
            onSelect()
        } else {
            keyboardController?.hide()
            // If finished typing and text is empty, and it was a draft, delete happens from commit.
            if (transientText != note.text || beforeState == null) {
                // Publish update for undo/redo exactly ONCE at the end of typing
                val afterState = currentState.copy(text = transientText, positionX = currentOffset.x, positionY = currentOffset.y)
                onCommit(beforeState, afterState)
            }
        }
    }

    // Available colors for quick-pick
    val colorOptions = remember {
        listOf(
            Color.Red, Color.Blue, Color.Green, Color.Black,
            Color.White, Color(0xFFFF9800), Color(0xFF9C27B0)
        )
    }
    
    // Toggle for color palette visibility in toolbar
    var showColors by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset { IntOffset(currentOffset.x.toInt(), currentOffset.y.toInt()) }
            .pointerInput(isEditMode && !isTyping) {
                if (isEditMode && !isTyping) {
                    detectDragGestures(
                        onDragEnd = {
                            if (currentOffset != note.position) {
                                val afterState = currentState.copy(positionX = currentOffset.x, positionY = currentOffset.y)
                                onCommit(currentState, afterState)
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        currentOffset += dragAmount
                        onSelect() // select while dragging
                    }
                }
            }
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures(
                        onTap = { 
                            if (!isTyping) onSelect()
                        }
                    )
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Toolbar (Shows above the text when selected but NOT actively typing)
            AnimatedVisibility(
                visible = isSelected && !isTyping && isEditMode,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showColors) {
                        // Inline Color Picker
                        colorOptions.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (c == color) 2.dp else 0.5.dp, if (c == color) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                                    .clickable {
                                        onCommit(currentState, currentState.copy(color = c.value.toLong()))
                                        showColors = false
                                    }
                            )
                        }
                        IconButton(onClick = { showColors = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close Colors", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        // Standard Toolbar Tools
                        IconButton(onClick = { isTyping = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Text", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showColors = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = "Change Color", tint = color, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onCommit(currentState, currentState.copy(fontSize = (fontSize - 2f).coerceAtLeast(10f))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.TextDecrease, contentDescription = "Decrease Font Size", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onCommit(currentState, currentState.copy(fontSize = (fontSize + 2f).coerceAtMost(72f))) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.TextIncrease, contentDescription = "Increase Font Size", modifier = Modifier.size(20.dp))
                        }
                        // Delete Button
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Text", tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Text Rendering / Input Field
            Box(
                modifier = Modifier
                    .background(
                        color = if (isTyping) Color(0xAAFFFFFF) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .then(
                        if (isSelected && !isTyping) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = if (isTyping) transientText else textToDisplay,
                        onValueChange = { if (isTyping) transientText = it },
                        textStyle = TextStyle(
                            color = color,
                            fontSize = with(LocalDensity.current) { fontSize.toSp() },
                            fontWeight = FontWeight.Normal
                        ),
                        enabled = isTyping,
                        modifier = Modifier.focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { isTyping = false }),
                        decorationBox = { innerTextField ->
                            Box {
                                if (transientText.isEmpty() && textToDisplay.isEmpty() && isTyping) {
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

                    // "Done" checkmark when actively typing
                    if (isTyping) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { isTyping = false },
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done Editing",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
