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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.TextAlignment
import com.pdf.pdfreader.domain.model.TextBlock
import com.pdf.pdfreader.domain.model.TextWord
import com.pdf.pdfreader.selection.hit.PdfWordHitTester
import com.pdf.pdfreader.ui.viewmodel.TextSelectionState

private const val TAG = "TextEditOverlay"

/** Compose layout max constraint = 262143. Clamp to stay safely under. */
private const val MAX_SIZE_PX = 262000

/**
 * Overlay composable that renders extracted text blocks as interactive regions.
 *
 * CRITICAL FIX: Now renders edited text content on top of the original PDF text.
 * Previously only highlight rectangles were drawn — the actual text edits were
 * never visible. Now:
 * 1. Un-edited blocks get a yellow highlight border (tap to edit)
 * 2. Edited blocks get a white background + the new text rendered on top,
 *    hiding the original PDF text underneath
 * 3. Selected block shows the inline editor (zIndex 50)
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
    onEditTextBlock: (blockId: String, newText: String, newFontSize: Float, newColor: Color) -> Unit,
    // Word-level selection for markup while in Edit-Text mode (same engine as reading mode).
    textSelection: TextSelectionState? = null,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel? = null
) {
    if (!isEditTextMode || pageSize == IntSize.Zero) return

    val pageWidth = pageSize.width.toFloat()
    val pageHeight = pageSize.height.toFloat()
    val density = LocalDensity.current

    val pageBlocks = remember(textBlocks, pageIndex) {
        textBlocks.filter { it.pageIndex == pageIndex }
    }

    // All words on this page in reading order — for word-level long-press selection.
    val allWords = remember(pageBlocks) {
        pageBlocks.flatMap { it.words }.sortedWith(compareBy({ it.y }, { it.x }))
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Layer 1: Highlight rectangles for UN-EDITED blocks (visual only) ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .drawBehind {
                    pageBlocks.forEach { block ->
                        val editedBlock = editedTextBlocks.find { it.originalBlock.id == block.id }
                        val isSelected = block.id == selectedTextBlockId

                        // Skip drawing highlight for edited blocks — they get a composable overlay
                        if (editedBlock != null) return@forEach

                        val rectX = block.x * pageWidth
                        val rectY = block.y * pageHeight
                        val rectW = block.width * pageWidth
                        val rectH = block.height * pageHeight

                        // Fill
                        drawRect(
                            color = when {
                                isSelected -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                else -> Color(0xFFFFC107).copy(alpha = 0.1f)
                            },
                            topLeft = Offset(rectX, rectY),
                            size = Size(rectW, rectH)
                        )

                        // Border
                        drawRect(
                            color = when {
                                isSelected -> Color(0xFF2196F3)
                                else -> Color(0xFFFFC107).copy(alpha = 0.5f)
                            },
                            topLeft = Offset(rectX, rectY),
                            size = Size(rectW, rectH),
                            style = Stroke(width = if (isSelected) 2.5f else 1f)
                        )
                    }
                }
        )

        // ─── Layer 2: Rendered EDITED text blocks (visible text overlays) ───
        // This is the CRITICAL FIX: previously edited text was never rendered.
        // Now we draw a white background + the new text on top of the original PDF text.
        pageBlocks.forEach { block ->
            val editedBlock = editedTextBlocks.find { it.originalBlock.id == block.id }
                ?: return@forEach

            // Don't render if currently being edited (editor is showing instead)
            if (block.id == selectedTextBlockId) return@forEach

            val rectX = (block.x * pageWidth).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectY = (block.y * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectW = (block.width * pageWidth).toInt().coerceIn(20, MAX_SIZE_PX)
            val rectH = (block.height * pageHeight).toInt().coerceIn(20, MAX_SIZE_PX)

            // Font size is stored in PDF points; scale to on-screen pixels so the
            // edited text matches the size of the surrounding PDF text.
            val fontScale = if (block.pdfPageWidth > 0f) pageWidth / block.pdfPageWidth else 1f

            val textAlign = when (editedBlock.alignment) {
                TextAlignment.LEFT -> TextAlign.Start
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.RIGHT -> TextAlign.End
            }

            // Animate appearance so it feels like in-place update, not a new overlay
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                label = "editedTextFadeIn"
            )

            Box(
                modifier = Modifier
                    .zIndex(3f)
                    .offset { IntOffset(rectX, rectY) }
                    .size(
                        width = with(density) { rectW.toDp() },
                        height = with(density) { rectH.toDp() }
                    )
                    // No white masking: the real text is replaced in the content stream on
                    // export. This is only an in-editor preview of the new text.
                    .graphicsLayer { this.alpha = alpha }
                    .padding(1.dp)
            ) {
                Text(
                    text = editedBlock.newText,
                    style = TextStyle(
                        color = editedBlock.newColor.copy(alpha = editedBlock.opacity),
                        fontSize = with(density) { (editedBlock.newFontSize * fontScale).toSp() },
                        fontWeight = FontWeight.Normal,
                        textAlign = textAlign
                    ),
                    maxLines = 10,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ─── Layer 3: Single gesture layer (zIndex 5) ───
        // Quick TAP → select the block for inline text replacement (unchanged behavior).
        // LONG-PRESS → select a single WORD for markup (highlight/underline/strike/color),
        // showing the drag handles below — never a whole line/paragraph. One gesture owner
        // (no per-block tap targets) so tap vs. long-press stay cleanly separated.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f)
                .pointerInput(pageBlocks, allWords) {
                    detectTapGestures(
                        onTap = { pos ->
                            val block = pageBlocks.firstOrNull { b ->
                                val bx = b.x * pageWidth
                                val by = b.y * pageHeight
                                pos.x >= bx && pos.x <= bx + b.width * pageWidth &&
                                    pos.y >= by && pos.y <= by + b.height * pageHeight
                            }
                            if (block != null) {
                                Log.d(TAG, "Text block tapped: id=${block.id}")
                                onSelectTextBlock(block.id)
                            }
                        },
                        onLongPress = { pos ->
                            val word = PdfWordHitTester.wordAt(pos, pageWidth.toInt(), pageHeight.toInt(), allWords)
                            if (word != null && editorViewModel != null) {
                                Log.d(TAG, "Word long-pressed for markup: '${word.text}'")
                                editorViewModel.startTextSelection(pageIndex, word, allWords)
                            }
                        }
                    )
                }
        )

        // ─── Layer 3b: Word-selection highlight + drag handles (zIndex 6) ───
        if (editorViewModel != null) {
            Box(modifier = Modifier.zIndex(6f)) {
                TextSelectionVisuals(
                    pageIndex = pageIndex,
                    pageWidth = pageWidth.toInt(),
                    pageHeight = pageHeight.toInt(),
                    textSelection = textSelection,
                    allWords = allWords,
                    editorViewModel = editorViewModel
                )
            }
        }

        // ─── Layer 4: Inline editor popup (zIndex 50) ───
        val selectedBlock = pageBlocks.find { it.id == selectedTextBlockId }
        if (selectedBlock != null) {
            val editedVersion = editedTextBlocks.find { it.originalBlock.id == selectedBlock.id }

            val blockX = (selectedBlock.x * pageWidth).toInt().coerceIn(0, MAX_SIZE_PX)
            val blockY = (selectedBlock.y * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)
            val blockH = (selectedBlock.height * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)

            Log.d(TAG, "Showing editor for: ${selectedBlock.id} at ($blockX, ${blockY + blockH + 8})")

            val editorFontScale = if (selectedBlock.pdfPageWidth > 0f) pageWidth / selectedBlock.pdfPageWidth else 1f

            Box(modifier = Modifier.zIndex(50f)) {
                TextEditInlineEditor(
                    block = selectedBlock,
                    editedBlock = editedVersion,
                    offsetX = blockX,
                    offsetY = blockY + blockH + 8,
                    pageWidth = pageWidth.toInt(),
                    fontScale = editorFontScale,
                    onConfirm = { newText, newFontSize, newColor ->
                        Log.d(TAG, "Text edit confirmed: $newText")
                        onEditTextBlock(selectedBlock.id, newText, newFontSize, newColor)
                        // onSelectTextBlock(null) is called by editTextBlock which sets selectedTextBlockId = null
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
 * `internal` (not private) so the OCR edit overlay (feature/pdf_ocr) reuses the
 * exact same editor popup instead of duplicating it.
 */
@Composable
internal fun TextEditInlineEditor(
    block: TextBlock,
    editedBlock: EditedTextBlock?,
    offsetX: Int,
    offsetY: Int,
    pageWidth: Int,
    fontScale: Float = 1f,
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
                    // Scale PDF points → display px (and keep it readable while typing).
                    fontSize = with(LocalDensity.current) {
                        (fontSize * fontScale).coerceAtLeast(14.dp.toPx()).toSp()
                    },
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
