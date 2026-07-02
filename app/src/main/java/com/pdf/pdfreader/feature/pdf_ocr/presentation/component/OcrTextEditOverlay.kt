package com.pdf.pdfreader.feature.pdf_ocr.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.feature.pdf_ocr.data.engine.OcrPatchGeometry
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrEditableWord
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWordEdit
import com.pdf.pdfreader.ui.components.TextEditInlineEditor

/** Compose layout max constraint = 262143. Clamp to stay safely under. */
private const val MAX_SIZE_PX = 262000

private val LowConfidenceColor = Color(0xFFFFA000) // amber
private val WordBorderColor = Color(0xFF4CAF50)    // green: recognized, editable
private val SelectedColor = Color(0xFF2196F3)      // blue

/**
 * Word-level editing overlay for OCR'd (scanned) pages — the OCR counterpart of
 * [com.pdf.pdfreader.ui.components.TextEditOverlay], sharing its geometry math
 * and inline editor but keeping all state in PdfOcrViewModel:
 *
 * - Layer 1: word boxes (thin green; amber when confidence < threshold)
 * - Layer 2: edited words — white patch + new text preview (the export draws the
 *   real background-sampled patch)
 * - Layer 3: per-word tap targets
 * - Layer 4: the shared [TextEditInlineEditor] for the selected word
 */
@Composable
fun OcrTextEditOverlay(
    modifier: Modifier = Modifier,
    pageIndex: Int,
    pageSize: IntSize,
    words: List<OcrEditableWord>,
    edits: Map<String, OcrWordEdit>,
    selectedWordId: String?,
    lowConfidenceThreshold: Float,
    onSelectWord: (String?) -> Unit,
    onEditWord: (id: String, newText: String, newFontSize: Float, newColor: Color) -> Unit
) {
    if (pageSize == IntSize.Zero) return

    val pageWidth = pageSize.width.toFloat()
    val pageHeight = pageSize.height.toFloat()
    val density = LocalDensity.current

    val pageWords = words.filter { it.pageIndex == pageIndex }
    if (pageWords.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {
        // ─── Layer 1: word boxes for un-edited words ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .drawBehind {
                    pageWords.forEach { word ->
                        if (edits.containsKey(word.id)) return@forEach
                        val isSelected = word.id == selectedWordId
                        val lowConfidence = word.confidence < lowConfidenceThreshold

                        val topLeft = Offset(word.block.x * pageWidth, word.block.y * pageHeight)
                        val size = Size(word.block.width * pageWidth, word.block.height * pageHeight)

                        val color = when {
                            isSelected -> SelectedColor
                            lowConfidence -> LowConfidenceColor
                            else -> WordBorderColor
                        }
                        if (isSelected || lowConfidence) {
                            drawRect(color = color.copy(alpha = 0.15f), topLeft = topLeft, size = size)
                        }
                        drawRect(
                            color = color.copy(alpha = if (isSelected) 1f else 0.6f),
                            topLeft = topLeft,
                            size = size,
                            style = Stroke(width = if (isSelected) 2.5f else 1.5f)
                        )
                    }
                }
        )

        // ─── Layer 2: edited words — patch + new text preview ───
        pageWords.forEach { word ->
            val edit = edits[word.id] ?: return@forEach
            if (word.id == selectedWordId) return@forEach // editor is showing instead

            // Grow the white preview patch by the same proportional rule the exporter
            // uses (OcrPatchGeometry), so on-screen preview matches the exported cover.
            val rawW = word.block.width * pageWidth
            val rawH = word.block.height * pageHeight
            val padX = OcrPatchGeometry.horizontalPadPx(rawW)
            val padY = OcrPatchGeometry.verticalPadPx(rawH)
            val rectX = (word.block.x * pageWidth - padX).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectY = (word.block.y * pageHeight - padY).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectW = (rawW + 2 * padX).toInt().coerceIn(8, MAX_SIZE_PX)
            val rectH = (rawH + 2 * padY).toInt().coerceIn(8, MAX_SIZE_PX)
            val fontScale =
                if (word.block.pdfPageWidth > 0f) pageWidth / word.block.pdfPageWidth else 1f

            Box(
                modifier = Modifier
                    .zIndex(3f)
                    .offset { IntOffset(rectX, rectY) }
                    .size(
                        width = with(density) { rectW.toDp() },
                        height = with(density) { rectH.toDp() }
                    )
                    // Preview of the exported patch: cover the scanned word's pixels.
                    .background(Color.White),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = edit.newText,
                    style = TextStyle(
                        color = edit.color,
                        fontSize = with(density) { (edit.fontSizePdf * fontScale).toSp() }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false
                )
            }
        }

        // ─── Layer 3: per-word tap targets ───
        pageWords.forEach { word ->
            val rectX = (word.block.x * pageWidth).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectY = (word.block.y * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)
            val rectW = (word.block.width * pageWidth).toInt().coerceIn(8, MAX_SIZE_PX)
            val rectH = (word.block.height * pageHeight).toInt().coerceIn(8, MAX_SIZE_PX)

            Box(
                modifier = Modifier
                    .zIndex(5f)
                    .offset { IntOffset(rectX, rectY) }
                    .size(
                        width = with(density) { rectW.toDp() },
                        height = with(density) { rectH.toDp() }
                    )
                    // Same consume pattern as TextEditOverlay: receive events even when
                    // a parent pointerInput consumed the down.
                    .pointerInput(word.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                onSelectWord(word.id)
                            }
                        }
                    }
            )
        }

        // ─── Layer 4: shared inline editor for the selected word ───
        val selected = pageWords.find { it.id == selectedWordId }
        if (selected != null) {
            val edit = edits[selected.id]
            val editedBlock = edit?.let {
                EditedTextBlock(
                    id = selected.id,
                    originalBlock = selected.block,
                    newText = it.newText,
                    newFontSize = it.fontSizePdf,
                    newColor = it.color
                )
            }

            val blockX = (selected.block.x * pageWidth).toInt().coerceIn(0, MAX_SIZE_PX)
            val blockY = (selected.block.y * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)
            val blockH = (selected.block.height * pageHeight).toInt().coerceIn(0, MAX_SIZE_PX)
            val fontScale =
                if (selected.block.pdfPageWidth > 0f) pageWidth / selected.block.pdfPageWidth else 1f

            Box(modifier = Modifier.zIndex(50f)) {
                TextEditInlineEditor(
                    block = selected.block,
                    editedBlock = editedBlock,
                    offsetX = blockX,
                    offsetY = blockY + blockH + 8,
                    pageWidth = pageWidth.toInt(),
                    fontScale = fontScale,
                    onConfirm = { newText, newFontSize, newColor ->
                        onEditWord(selected.id, newText, newFontSize, newColor)
                    },
                    onDismiss = { onSelectWord(null) }
                )
            }
        }
    }
}
