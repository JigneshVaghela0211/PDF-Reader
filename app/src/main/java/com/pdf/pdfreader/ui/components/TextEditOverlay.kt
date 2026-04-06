package com.pdf.pdfreader.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.TextBlock

private const val TAG = "TextEditOverlay"

/**
 * Overlay composable that renders extracted text blocks as interactive regions.
 *
 * Fix notes:
 * - Uses pointerInput + detectTapGestures instead of clickable modifier
 *   (clickable doesn't propagate correctly in overlay stacks)
 * - Each text block is a separate composable with its own tap handler
 * - Tap targets have zIndex(5) to be above the highlight canvas
 * - Editor popup has zIndex(50) to be above everything
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
    val density = LocalDensity.current

    val pageBlocks = remember(textBlocks, pageIndex) {
        textBlocks.filter { it.pageIndex == pageIndex }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Layer 1: Highlight rectangles (visual only, no interaction) ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .drawBehind {
                    pageBlocks.forEach { block ->
                        val editedBlock = editedTextBlocks.find { it.originalBlock.id == block.id }
                        val isSelected = block.id == selectedTextBlockId

                        val rectX = block.x * pageWidth
                        val rectY = block.y * pageHeight
                        val rectW = block.width * pageWidth
                        val rectH = block.height * pageHeight

                        // Fill
                        drawRect(
                            color = when {
                                isSelected -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                editedBlock != null -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else -> Color(0xFFFFC107).copy(alpha = 0.1f)
                            },
                            topLeft = Offset(rectX, rectY),
                            size = Size(rectW, rectH)
                        )

                        // Border
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

        // ─── Layer 2: Tap targets for each text block (zIndex 5) ───
        pageBlocks.forEach { block ->
            val rectX = (block.x * pageWidth).toInt()
            val rectY = (block.y * pageHeight).toInt()
            val rectW = (block.width * pageWidth).toInt().coerceAtLeast(20)
            val rectH = (block.height * pageHeight).toInt().coerceAtLeast(20)

            Box(
                modifier = Modifier
                    .zIndex(5f)
                    .offset { IntOffset(rectX, rectY) }
                    .size(
                        width = with(density) { rectW.toDp() },
                        height = with(density) { rectH.toDp() }
                    )
                    .pointerInput(block.id) {
                        detectTapGestures {
                            Log.d(TAG, "Text block tapped: id=${block.id} text='${block.text.take(30)}'")
                            onSelectTextBlock(block.id)
                        }
                    }
            )
        }

        // ─── Layer 3: Inline editor popup (zIndex 50) ───
        val selectedBlock = pageBlocks.find { it.id == selectedTextBlockId }
        if (selectedBlock != null) {
            val editedVersion = editedTextBlocks.find { it.originalBlock.id == selectedBlock.id }

            val blockX = (selectedBlock.x * pageWidth).toInt()
            val blockY = (selectedBlock.y * pageHeight).toInt()
            val blockH = (selectedBlock.height * pageHeight).toInt()

            Log.d(TAG, "Showing editor for: ${selectedBlock.id} at ($blockX, ${blockY + blockH + 8})")

            Box(modifier = Modifier.zIndex(50f)) {
                TextEditInlineEditor(
                    block = selectedBlock,
                    editedBlock = editedVersion,
                    offsetX = blockX,
                    offsetY = blockY + blockH + 8,
                    pageWidth = pageWidth.toInt(),
                    onConfirm = { newText, newFontSize, newColor ->
                        Log.d(TAG, "Text edit confirmed: $newText")
                        onEditTextBlock(selectedBlock.id, newText, newFontSize, newColor)
                        onSelectTextBlock(null)
                    },
                    onDismiss = {
                        Log.d(TAG, "Text edit dismissed")
                        onSelectTextBlock(null)
                    }
                )
            }
        }
    }
}

/**
 * Inline editor that appears below the selected text block.
 */
@Composable
private fun TextEditInlineEditor(
    block: TextBlock,
    editedBlock: EditedTextBlock?,
    offsetX: Int,
    offsetY: Int,
    pageWidth: Int,
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

    // Clamp offset so editor doesn't overflow page
    val clampedX = offsetX.coerceIn(8, (pageWidth - 320).coerceAtLeast(8))

    Box(
        modifier = Modifier
            .offset { IntOffset(clampedX, offsetY) }
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
            // Consume taps so they don't propagate to parent
            .pointerInput(Unit) {
                detectTapGestures { /* consume tap */ }
            }
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
