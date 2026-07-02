package com.pdf.pdfreader.feature.pdf_ocr.domain

import com.pdf.pdfreader.domain.model.TextBlock
import com.pdf.pdfreader.domain.model.TextWord
import com.pdf.pdfreader.feature.pdf_ocr.data.engine.OcrCoordinateMapper
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrEditableWord
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage

/**
 * Bridges the OCR hierarchy into the app's existing text-editing geometry: each
 * recognized word becomes a word-sized [TextBlock], so the overlay and the shared
 * inline editor scale fonts/positions with exactly the same math as real text
 * editing (`fontScale = renderedPageWidthPx / pdfPageWidth`).
 *
 * `pdfPageWidth/Height` are set to the page's *displayed* size in points
 * (crop box swapped for /Rotate 90/270), because the normalized OCR coords are
 * display-space.
 */
object OcrToTextBlockMapper {

    fun toEditableWords(page: OcrPage): List<OcrEditableWord> {
        val (dispW, dispH) = OcrCoordinateMapper.displayedSize(
            page.pdfPageWidth, page.pdfPageHeight, page.pageRotation
        )
        return page.blocks.flatMapIndexed { bi, block ->
            block.lines.flatMapIndexed { li, line ->
                line.words.mapIndexed { wi, word ->
                    OcrEditableWord(
                        block = TextBlock(
                            id = "ocr_${page.pageIndex}_${bi}_${li}_$wi",
                            pageIndex = page.pageIndex,
                            text = word.text,
                            x = word.x,
                            y = word.y,
                            width = word.width,
                            height = word.height,
                            fontSize = (word.height * dispH).coerceIn(1f, 300f),
                            fontName = "OCR",
                            words = listOf(TextWord(word.text, word.x, word.y, word.width, word.height)),
                            pdfPageWidth = dispW,
                            pdfPageHeight = dispH
                        ),
                        word = word,
                        confidence = word.confidence
                    )
                }
            }
        }
    }
}
