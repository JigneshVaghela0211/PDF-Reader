package com.pdf.pdfreader.utiles

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector

/**
 * Detects the *actual* text-showing operators on a PDF page and extracts the real
 * formatting metadata of each one straight from the content stream.
 *
 * This is the "select the real PDF text object" step of true text editing: given the
 * rectangle of a tapped text block, [detectInRegion] returns the show-text operators whose
 * glyphs fall inside it, each carrying everything needed to re-create the text faithfully:
 *  - the text value,
 *  - the embedded [PDFont] actually used,
 *  - the font size,
 *  - the fill (non-stroking) colour,
 *  - the text-rendering [Matrix] (position + rotation + scale, with the page CTM folded in),
 *  - the glyph bounding box (PDF points, origin bottom-left).
 *
 * Show-operation indices are assigned in content-stream order, one per `Tj`/`'`/`"` and one
 * per `COSString` segment inside a `TJ` array — identical to how [PdfContentStreamEditor]
 * walks the raw tokens, so the two stay in lock-step.
 */
class PdfTextObjectDetector {

    companion object {
        private const val TAG = "PdfTextObjectDetector"
        /** Padding (PDF points) around a target region when testing glyph origins. */
        private const val PAD = 2f
    }

    /** A single detected text-showing operator with its real formatting. */
    data class DetectedTextOp(
        val showIndex: Int,
        val text: String,
        val font: PDFont,
        val fontSize: Float,
        /** Text-rendering matrix at the op's first glyph (font size folded in). */
        val renderMatrix: Matrix,
        val colorRgb: Int,
        val left: Float,
        val bottom: Float,
        val right: Float,
        val top: Float
    )

    /**
     * Return the show-text operators on page [pageIndex] (0-based) whose glyphs fall inside
     * [region] (PDF points, origin bottom-left), in content-stream order.
     */
    fun detectInRegion(
        document: PDDocument,
        pageIndex: Int,
        region: Region
    ): List<DetectedTextOp> {
        val collector = OpCollector().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
            sortByPosition = false
        }
        return try {
            collector.getText(document)
            collector.ops.filter { op ->
                val cx = (op.left + op.right) / 2f
                val cy = (op.bottom + op.top) / 2f
                region.contains(op.left, op.bottom) || region.contains(op.right, op.top) ||
                    region.contains(cx, cy)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Detection failed on page $pageIndex", e)
            emptyList()
        }
    }

    /** A target rectangle in PDF user-space points (origin bottom-left). */
    data class Region(
        val left: Float,
        val bottom: Float,
        val right: Float,
        val top: Float
    ) {
        fun contains(x: Float, y: Float): Boolean =
            x >= left - PAD && x <= right + PAD && y >= bottom - PAD && y <= top + PAD
    }

    /**
     * Walks the page and records one [DetectedTextOp] per `showText` invocation.
     */
    private class OpCollector : PDFTextStripper() {

        val ops = ArrayList<DetectedTextOp>()

        private var showIndex = 0
        private var sb = StringBuilder()
        private var firstMatrix: Matrix? = null
        private var minX = 0f
        private var minY = 0f
        private var maxX = 0f
        private var maxY = 0f
        private var haveBox = false

        override fun showGlyph(
            textRenderingMatrix: Matrix,
            font: PDFont,
            code: Int,
            unicode: String?,
            displacement: Vector
        ) {
            if (firstMatrix == null) firstMatrix = textRenderingMatrix.clone()
            val x = textRenderingMatrix.translateX
            val y = textRenderingMatrix.translateY
            // Approximate glyph extent: width from displacement, height from matrix scale.
            val w = displacement.x * textRenderingMatrix.scalingFactorX
            val h = textRenderingMatrix.scalingFactorY
            if (!haveBox) {
                minX = x; minY = y; maxX = x + w; maxY = y + h; haveBox = true
            } else {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x + w > maxX) maxX = x + w
                if (y + h > maxY) maxY = y + h
            }
            if (unicode != null) sb.append(unicode)
            super.showGlyph(textRenderingMatrix, font, code, unicode, displacement)
        }

        override fun showText(string: ByteArray) {
            // Reset per-op accumulators.
            sb = StringBuilder()
            firstMatrix = null
            haveBox = false

            val font = graphicsState.textState.font
            val fontSize = graphicsState.textState.fontSize
            val colorRgb = try { graphicsState.nonStrokingColor.toRGB() } catch (_: Exception) { 0 }

            super.showText(string)

            val m = firstMatrix
            if (m != null && haveBox && font != null) {
                ops.add(
                    DetectedTextOp(
                        showIndex = showIndex,
                        text = sb.toString(),
                        font = font,
                        fontSize = fontSize,
                        renderMatrix = m,
                        colorRgb = colorRgb,
                        left = minX, bottom = minY, right = maxX, top = maxY
                    )
                )
            }
            showIndex++
        }
    }
}
