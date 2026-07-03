package com.pdf.pdfreader.utiles

import com.pdf.pdfreader.selection.model.SelectionEditRequest
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont

/**
 * Micro Chunk 4: decides whether a [SelectionEditRequest] can be safely replaced in place.
 *
 * It ONLY analyzes — it never mutates the PDF and never calls [PdfTextReplacementEngine]. It reuses
 * the existing [PdfTextObjectDetector] to read the real show-text operators under the selection, then
 * applies conservative "safe before clever" rules:
 *
 *  - **[ReplacementDecision.Reject]** — nothing to replace (no operator intersects the selection).
 *  - **[ReplacementDecision.RegionReplace]** — the selection is exactly ONE self-contained operator
 *    whose embedded font can encode the new text ⇒ an in-place swap won't touch neighbours.
 *  - **[ReplacementDecision.Fallback]** — anything riskier: the operator also holds neighbouring
 *    glyphs, the font can't encode the new text, or the selection spans several operators
 *    (kerned `TJ` / multiple words or lines). The existing block-level fallback should handle these.
 *
 * The output is a pure [ReplacementDecision]; wiring it to an actual replacement is a later chunk.
 */
class ReplacementAnalyzer(
    private val detector: PdfTextObjectDetector = PdfTextObjectDetector()
) {

    /**
     * Analyze [request] against the already-open [document]. Read-only; no side effects.
     */
    fun analyze(document: PDDocument, request: SelectionEditRequest): ReplacementDecision {
        if (request.words.isEmpty()) {
            return ReplacementDecision.Reject("Selection contains no words.")
        }
        if (request.pageIndex < 0 || request.pageIndex >= document.numberOfPages) {
            return ReplacementDecision.Reject("Page ${request.pageIndex} is out of range.")
        }

        val region = regionOf(document, request)
        val ops = detector.detectInRegion(document, request.pageIndex, region)
        if (ops.isEmpty()) {
            return ReplacementDecision.Reject(
                "No text-showing operator intersects the selection region — the text may be an " +
                    "image/scan or otherwise non-extractable, so there is nothing to replace in place."
            )
        }

        val selText = normalize(request.originalText)
        val newText = request.newText

        // ── Single operator: the only case that can be a safe in-place region replace ──
        if (ops.size == 1) {
            val op = ops.first()
            val opText = normalize(op.text)
            return when {
                opText.length > selText.length && opText.contains(selText) ->
                    ReplacementDecision.Fallback(
                        reason = "The selection shares a single show-text operator (\"${op.text.trim()}\") with " +
                            "neighbouring glyphs; an in-place replace would alter that neighbouring text. " +
                            "Use the block-level fallback.",
                        fallbackType = FallbackType.OVERLAPPING_REGION
                    )

                !canEncode(op.font, newText) ->
                    ReplacementDecision.Fallback(
                        reason = "The embedded font \"${op.font.name}\" cannot encode the new text " +
                            "(subset/missing glyphs); fall back to a re-draw with a matched standard font.",
                        fallbackType = FallbackType.UNSUPPORTED_ENCODING
                    )

                opText == selText ->
                    ReplacementDecision.RegionReplace(
                        reason = "The selection is exactly one self-contained show-text operator whose font " +
                            "\"${op.font.name}\" can encode the new text — an in-place swap is safe and " +
                            "leaves neighbouring text untouched.",
                        confidence = 0.95f
                    )

                else ->
                    ReplacementDecision.Fallback(
                        reason = "The detected operator text (\"${op.text.trim()}\") does not cleanly match the " +
                            "selection (\"${request.originalText.trim()}\"); the mapping is ambiguous, so " +
                            "use the safe fallback rather than risk a wrong edit.",
                        fallbackType = FallbackType.UNKNOWN
                    )
            }
        }

        // ── Multiple operators: kerned TJ / multi-word / multi-line — never an in-place sub-span ──
        return ReplacementDecision.Fallback(
            reason = "The selection spans ${ops.size} separate show-text operators (kerned text, or multiple " +
                "words/lines); replacing part of that run in place is unsafe. Use the block-level fallback.",
            fallbackType = FallbackType.MULTI_OPERATOR
        )
    }

    /** Union of the request's normalized word boxes → a PDF-point region (origin bottom-left). */
    private fun regionOf(document: PDDocument, request: SelectionEditRequest): PdfTextObjectDetector.Region {
        val crop = document.getPage(request.pageIndex).cropBox
        val pw = crop.width
        val ph = crop.height
        val left = request.words.minOf { it.x }
        val top = request.words.minOf { it.y }
        val right = request.words.maxOf { it.x + it.width }
        val bottom = request.words.maxOf { it.y + it.height }
        return PdfTextObjectDetector.Region(
            left = left * pw,
            bottom = ph - bottom * ph,
            right = right * pw,
            top = ph - top * ph
        )
    }

    private fun canEncode(font: PDFont, text: String): Boolean = try {
        font.encode(text)
        true
    } catch (_: Exception) {
        false
    }

    /** Trim and collapse internal whitespace so operator/selection text compare cleanly. */
    private fun normalize(s: String): String = s.trim().replace(Regex("\\s+"), " ")
}
