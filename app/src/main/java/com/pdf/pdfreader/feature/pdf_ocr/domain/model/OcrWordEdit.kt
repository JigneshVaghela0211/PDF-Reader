package com.pdf.pdfreader.feature.pdf_ocr.domain.model

import androidx.compose.ui.graphics.Color
import com.pdf.pdfreader.domain.model.TextBlock

/**
 * One OCR word presented for editing: the word's geometry re-expressed as a
 * word-sized [TextBlock] (so the existing overlay/editor geometry math applies
 * unchanged) plus the OCR-specific bits the shared models don't carry.
 */
data class OcrEditableWord(
    val block: TextBlock,
    val word: OcrWord,
    val confidence: Float
) {
    val id: String get() = block.id
    val pageIndex: Int get() = block.pageIndex
}

/**
 * A user edit of one OCR word ("Invoice" → "Receipt"). Kept strictly separate
 * from [com.pdf.pdfreader.domain.model.EditedTextBlock]: OCR edits are exported
 * as background patch + visible text, never through PdfTextReplacementEngine.
 */
data class OcrWordEdit(
    val id: String,
    val pageIndex: Int,
    /** The recognized word being replaced — also the exclusion key for the invisible layer. */
    val originalWord: OcrWord,
    val newText: String,
    /** Font size in PDF points (display size derives via the page fontScale). */
    val fontSizePdf: Float,
    val color: Color = Color.Black
)
