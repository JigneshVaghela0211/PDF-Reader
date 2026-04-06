package com.pdf.pdfreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.TextBlock

/**
 * Overlay composable that renders extracted text blocks as interactive regions
 * on top of the PDF page bitmap. When EDIT_TEXT tool is active, users can:
 * - See all detected text blocks highlighted
 * - Tap a block to select it
 * - Edit the selected block's content via a floating editor
 */
@Composable
fun TextEditOverlay(
    modifier: Modifier = Modifier,
    pageIndex: Int,
    pageSize: IntSize,
    textBlocks: List<TextBlock>,
    editedTextBlocks: List<EditedTextBlock>,
    selectedTextBlockId: String?,
    isEditTextMode: Boolean,
    onSelectTextBlock: (String?) -> Unit,
    onEditTextBlock: (blockId: String, newText: String, newFontSize: Float, newColor: Color) -> Unit
) {
    if (!isEditTextMode || pageSize == IntSize.Zero) return

    val pageWidth = pageSize.width.toFloat()
    val pageHeight = pageSize.height.toFloat()

    Box(modifier = modifier.fillMaxSize()) {
        // Draw highlight rectangles for all text blocks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    textBlocks.filter { it.pageIndex == pageIndex }.forEach { block ->
                        val editedBlock = editedTextBlocks.find { it.originalBlock.id == block.id }
                        val isSelected = block.id == selectedTextBlockId

                        val rectX = block.x * pageWidth
                        val rectY = block.y * pageHeight
                        val rectW = block.width * pageWidth
                        val rectH = block.height * pageHeight

                        // Draw highlight rectangle
                        drawRect(
                            color = when {
                                isSelected -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                editedBlock != null -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else -> Color(0xFFFFC107).copy(alpha = 0.1f)
                            },
                            topLeft = Offset(rectX, rectY),
                            size = Size(rectW, rectH)
                        )

                        // Draw border
                        drawRect(
                            color = when {
                                isSelected -> Color(0xFF2196F3)
                                editedBlock != null -> Color(0xFF4CAF50)
                                else -> Color(0xFFFFC107).copy(alpha = 0.5f)
                            },
                            topLeft = Offset(rectX, rectY),
                            size = Size(rectW, rectH),
                            style = Stroke(width = if (isSelected) 2.5f else 1f)
                        )
                    }
                }
        )

        // Render clickable tap targets for each text block
        textBlocks.filter { it.pageIndex == pageIndex }.forEach { block ->
            val rectX = (block.x * pageWidth).toInt()
            val rectY = (block.y * pageHeight).toInt()
            val rectW = (block.width * pageWidth).toInt()
            val rectH = (block.height * pageHeight).toInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(rectX, rectY) }
                    .size(
                        width = with(LocalDensity.current) { rectW.toDp() },
                        height = with(LocalDensity.current) { rectH.toDp() }
                    )
                    .clickable { onSelectTextBlock(block.id) }
            )
        }

        // Show inline edit dialog for selected text block
        val selectedBlock = textBlocks.find { it.id == selectedTextBlockId && it.pageIndex == pageIndex }
        if (selectedBlock != null) {
            val editedVersion = editedTextBlocks.find { it.originalBlock.id == selectedBlock.id }

            val blockX = (selectedBlock.x * pageWidth).toInt()
            val blockY = (selectedBlock.y * pageHeight).toInt()
            val blockH = (selectedBlock.height * pageHeight).toInt()

            TextEditInlineEditor(
                block = selectedBlock,
                editedBlock = editedVersion,
                offsetX = blockX,
                offsetY = blockY + blockH + 8,
                onConfirm = { newText, newFontSize, newColor ->
                    onEditTextBlock(selectedBlock.id, newText, newFontSize, newColor)
                    onSelectTextBlock(null)
                },
                onDismiss = { onSelectTextBlock(null) }
            )
        }
    }
}

/**
 * Inline editor that appears below the selected text block,
 * allowing the user to modify text content, font size, and color.
 */
@Composable
private fun TextEditInlineEditor(
    block: TextBlock,
    editedBlock: EditedTextBlock?,
    offsetX: Int,
    offsetY: Int,
    onConfirm: (String, Float, Color) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(block.id) {
        mutableStateOf(editedBlock?.newText ?: block.text)
    }
    var fontSize by remember(block.id) {
        mutableFloatStateOf(editedBlock?.newFontSize ?: block.fontSize)
    }
    var color by remember(block.id) {
        mutableStateOf(editedBlock?.newColor ?: Color.Black)
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(block.id) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.coerceAtLeast(8), offsetY) }
            .widthIn(min = 200.dp, max = 320.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Text input
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                textStyle = TextStyle(
                    color = color,
                    fontSize = with(LocalDensity.current) { fontSize.toSp() },
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    onConfirm(text, fontSize, color)
                }),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "Enter text…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Font size & color controls
            TextEditToolbar(
                fontSize = fontSize,
                color = color,
                onFontSizeChange = { fontSize = it },
                onColorChange = { color = it },
                onConfirm = {
                    keyboardController?.hide()
                    onConfirm(text, fontSize, color)
                },
                onCancel = {
                    keyboardController?.hide()
                    onDismiss()
                }
            )
        }
    }
}
