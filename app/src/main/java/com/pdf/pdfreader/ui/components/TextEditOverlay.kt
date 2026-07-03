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
    // Word-level selection + draggable handles in Edit-Text mode (shared selection engine).
    textSelection: TextSelectionState? = null,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel? = null,
    // True inline word editing: tap → edit the word in place; TextReplacementPreviewLayer shows edits.
    inlineEditWord: TextWord? = null,
    previewEdits: List<com.pdf.pdfreader.selection.model.WordPreviewEdit> = emptyList(),
    // (Debug D1) resolved-op geometry for the visual baseline/bbox overlay (empty in release/off).
    debugOps: List<com.pdf.pdfreader.feature.reader.data.engine.DebugWordOp> = emptyList(),
    onBeginEdit: (TextWord) -> Unit = {},
    onCommitEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {}
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

        // Displayed font size (px) for a word, from its block's PDF font size (fallback: glyph box).
        fun fontPxFor(word: TextWord): Float {
            val block = pageBlocks.firstOrNull { it.words.contains(word) }
            return if (block != null && block.pdfPageWidth > 0f) {
                block.fontSize * (pageWidth / block.pdfPageWidth)
            } else {
                word.height * pageHeight * 0.82f
            }
        }

        // ─── Text Replacement Preview Layer (zIndex 7): committed edits over their words ───
        // The original glyphs are already gone (the page is showing the renderer-suppressed edit
        // bitmap), so this only paints the new text into the empty slot — no cover / patch.
        TextReplacementPreviewLayer(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            previewEdits = previewEdits,
            activeWord = inlineEditWord,
            fontPxFor = ::fontPxFor
        )

        // ─── (Debug D1) Visual overlay (zIndex 8): baseline vs bbox vs matrix origin ───
        // MAGENTA = real glyph bbox · YELLOW = true PDF baseline (text-matrix origin y) ·
        // RED dot = matrix origin · CYAN = the selection bbox the TextField is anchored to.
        // The vertical gap between CYAN top and YELLOW baseline is exactly the editor offset.
        if (com.pdf.pdfreader.feature.reader.data.engine.SuppressionDebug.VISUAL &&
            (debugOps.isNotEmpty() || inlineEditWord != null || previewEdits.isNotEmpty())
        ) {
            val selWords = (listOfNotNull(inlineEditWord) + previewEdits.map { it.word }).distinct()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(8f)
                    .drawBehind {
                        // Selection bbox (what we currently position the editor with).
                        selWords.forEach { w ->
                            drawRect(
                                color = Color(0xFF00E5FF),
                                topLeft = Offset(w.x * pageWidth, w.y * pageHeight),
                                size = Size(w.width * pageWidth, w.height * pageHeight),
                                style = Stroke(width = 2f)
                            )
                        }
                        // Real detected-op glyph bbox + true baseline + matrix origin.
                        debugOps.forEach { op ->
                            val left = op.leftN * pageWidth
                            val right = op.rightN * pageWidth
                            val top = op.topN * pageHeight
                            val bottom = op.bottomN * pageHeight
                            val baseY = op.baselineN * pageHeight
                            drawRect(
                                color = Color(0xFFFF00FF),
                                topLeft = Offset(left, top),
                                size = Size((right - left), (bottom - top)),
                                style = Stroke(width = 1.5f)
                            )
                            drawLine(
                                color = Color(0xFFFFEB3B),
                                start = Offset(left, baseY),
                                end = Offset(right, baseY),
                                strokeWidth = 2f
                            )
                            drawCircle(
                                color = Color.Red,
                                radius = 4f,
                                center = Offset(left, baseY)
                            )
                        }
                    }
            )
        }

        // ─── Gesture layer (zIndex 5): TAP → inline-edit the nearest word in place ───
        // (Supersedes MC2's tap-to-select+handles in Edit-Text mode; the selection engine and
        //  reading-mode selection are unchanged.)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f)
                .pointerInput(pageBlocks, allWords) {
                    detectTapGestures(onTap = { pos ->
                        val word = PdfWordHitTester.wordAt(pos, pageWidth.toInt(), pageHeight.toInt(), allWords)
                        if (word != null) {
                            Log.d(TAG, "Inline edit begin on word: '${word.text}'")
                            onBeginEdit(word)
                        }
                    })
                }
        )

        // ─── Inline editor (zIndex 9): the TextField positioned exactly over the word ───
        if (inlineEditWord != null) {
            val w = inlineEditWord
            val initial = previewEdits.firstOrNull { it.word == w }?.newText ?: w.text
            InlineWordEditor(
                initialText = initial,
                fontPx = fontPxFor(w),
                offsetX = (w.x * pageWidth).toInt().coerceIn(0, MAX_SIZE_PX),
                offsetY = (w.y * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX),
                width = (w.width * pageWidth).toInt().coerceIn(24, MAX_SIZE_PX),
                height = (w.height * pageHeight).toInt().coerceIn(16, MAX_SIZE_PX),
                onDone = onCommitEdit,
                onCancel = onCancelEdit
            )
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
