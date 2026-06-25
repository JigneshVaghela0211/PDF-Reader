package com.pdf.pdfreader.utiles

import android.util.Log
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Performs *real* in-place text replacement on a PDF page — the core of true text editing.
 *
 * For each edited block it locates the actual show-text operators ([PdfTextObjectDetector]),
 * then replaces them with a tiered strategy:
 *
 *  - **Tier 1 (exact):** when the block maps to a single show-text operator whose embedded
 *    font can encode the new characters, the operator's operand bytes are swapped in place
 *    ([PdfContentStreamEditor]). Font, size, colour, position, rotation and the transformation
 *    matrix are all preserved implicitly because the surrounding operators are untouched.
 *
 *  - **Tier 2 (fallback):** otherwise (subset fonts missing glyphs, multi-operator/kerned or
 *    multi-line text) the original glyphs are removed from the content stream and the new text
 *    is drawn with the closest standard font, re-using the captured text-rendering matrix
 *    (position + rotation + size) and fill colour. Still real, selectable, searchable text —
 *    no white box, no overlay.
 */
class PdfTextReplacementEngine(
    private val detector: PdfTextObjectDetector = PdfTextObjectDetector(),
    private val contentEditor: PdfContentStreamEditor = PdfContentStreamEditor()
) {

    companion object {
        private const val TAG = "PdfTextReplacementEngine"
    }

    /**
     * Replace the text of [block] on page [pageIndex] inside [document].
     * @return true if the page content stream was modified.
     */
    fun replaceText(document: PDDocument, pageIndex: Int, block: EditedTextBlock): Boolean {
        if (pageIndex < 0 || pageIndex >= document.numberOfPages) return false
        val page = document.getPage(pageIndex)
        val cropBox = page.cropBox
        val pdfWidth = cropBox.width
        val pdfHeight = cropBox.height

        val original = block.originalBlock
        val left = original.x * pdfWidth
        val bottom = pdfHeight - (original.y * pdfHeight) - (original.height * pdfHeight)
        val region = PdfTextObjectDetector.Region(
            left = left,
            bottom = bottom,
            right = left + original.width * pdfWidth,
            top = bottom + original.height * pdfHeight
        )

        val ops = detector.detectInRegion(document, pageIndex, region)
        if (ops.isEmpty()) {
            Log.d(TAG, "No real text object found for block; nothing replaced")
            return false
        }

        val newText = block.newText

        // ── Tier 1: single operator, original font can encode the new text ──
        if (ops.size == 1) {
            val op = ops.first()
            val encoded = tryEncode(op.font, newText)
            if (encoded != null) {
                val ok = contentEditor.rewriteShowOps(
                    document, page, mapOf(op.showIndex to COSString(encoded))
                )
                if (ok) {
                    Log.d(TAG, "Tier-1 in-place replacement using ${op.font.name}")
                    return true
                }
            }
        }

        // ── Tier 2: remove originals, redraw with a matched standard font ──
        val removed = contentEditor.rewriteShowOps(
            document, page, ops.associate { it.showIndex to null }
        )
        if (!removed) {
            Log.w(TAG, "Tier-2 could not remove original glyphs; aborting to avoid double text")
            return false
        }
        drawReplacement(document, pageIndex, block, ops.first())
        Log.d(TAG, "Tier-2 fallback replacement with standard font")
        return true
    }

    /** Encode [text] with [font], or null if the font can't represent some character. */
    private fun tryEncode(font: PDFont, text: String): ByteArray? = try {
        font.encode(text)
    } catch (_: Exception) {
        null
    }

    /**
     * Draw the new text where the original block was. Position and size come from the block's
     * normalized bounds (the same math the original overlay used, which positioned correctly);
     * font family and colour come from the detected original op, and the rotation angle is
     * derived from its text-rendering matrix so rotated text stays rotated.
     */
    private fun drawReplacement(
        document: PDDocument,
        pageIndex: Int,
        block: EditedTextBlock,
        op: PdfTextObjectDetector.DetectedTextOp
    ) {
        val page = document.getPage(pageIndex)
        val cropBox = page.cropBox
        val pdfWidth = cropBox.width
        val pdfHeight = cropBox.height

        val b = block.originalBlock
        // Block bounds → PDF points (origin bottom-left). Mirrors the original overlay's
        // proven coordinate mapping (PdfExportManager's former drawTextEdit).
        val rectX = b.x * pdfWidth
        val rectY = pdfHeight - (b.y * pdfHeight) - (b.height * pdfHeight)
        val rectH = b.height * pdfHeight

        // Use the edited/extracted size (matches the previously-correct overlay positioning);
        // fall back to the detected on-page size only if it's missing.
        val fontSize = block.newFontSize.takeIf { it > 0f }
            ?: b.fontSize.takeIf { it > 0f }
            ?: op.renderMatrix.scalingFactorY
        val baselineY = rectY + rectH - fontSize

        // Rotation angle (degrees) from the original text-rendering matrix.
        val angleDeg = Math.toDegrees(
            kotlin.math.atan2(
                op.renderMatrix.getValue(0, 1).toDouble(),
                op.renderMatrix.getValue(0, 0).toDouble()
            )
        )

        Log.d(
            TAG,
            "Tier-2 draw: text='${block.newText}' rectX=$rectX baselineY=$baselineY " +
                "fontSize=$fontSize angle=$angleDeg page=${pdfWidth}x$pdfHeight " +
                "block(x=${b.x},y=${b.y},w=${b.width},h=${b.height}) font=${op.font.name}"
        )
        PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.APPEND, true, true
        ).use { cs ->
            cs.beginText()
            cs.setNonStrokingColor(
                (op.colorRgb shr 16) and 0xFF,
                (op.colorRgb shr 8) and 0xFF,
                op.colorRgb and 0xFF
            )
            cs.setFont(matchStandardFont(op.font.name), fontSize)

            if (kotlin.math.abs(angleDeg) > 0.5) {
                cs.setTextRotation(Math.toRadians(angleDeg), rectX.toDouble(), baselineY.toDouble())
            } else {
                cs.newLineAtOffset(rectX, baselineY)
            }

            val lines = block.newText.split("\n")
            for ((i, line) in lines.withIndex()) {
                if (i > 0) cs.newLineAtOffset(0f, -fontSize * 1.2f)
                showSafely(cs, line)
            }
            cs.endText()
        }
    }

    private fun showSafely(cs: PDPageContentStream, line: String) {
        try {
            cs.showText(line)
        } catch (_: Exception) {
            // Standard Type1 fonts only cover WinAnsi; replace unsupported chars.
            val sanitized = line.replace(Regex("[^\\x20-\\x7E]"), "?")
            try { cs.showText(sanitized) } catch (_: Exception) {}
        }
    }

    /** Pick the closest standard 14 font from an embedded font's name. */
    private fun matchStandardFont(fontName: String?): PDFont {
        val n = (fontName ?: "").lowercase()
        val bold = n.contains("bold")
        val italic = n.contains("italic") || n.contains("oblique")
        return when {
            n.contains("times") || n.contains("serif") || n.contains("georgia") || n.contains("roman") -> when {
                bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
                bold -> PDType1Font.TIMES_BOLD
                italic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            n.contains("courier") || n.contains("mono") || n.contains("consol") -> when {
                bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
                bold -> PDType1Font.COURIER_BOLD
                italic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
            else -> when {
                bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                bold -> PDType1Font.HELVETICA_BOLD
                italic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
        }
    }
}
