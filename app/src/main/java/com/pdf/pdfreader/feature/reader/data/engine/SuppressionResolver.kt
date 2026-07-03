package com.pdf.pdfreader.feature.reader.data.engine

import android.util.Log
import com.pdf.pdfreader.domain.model.TextWord
import com.pdf.pdfreader.selection.model.SelectionEditRequest
import com.pdf.pdfreader.utiles.PdfTextObjectDetector
import com.pdf.pdfreader.utiles.ReplacementAnalyzer
import com.pdf.pdfreader.utiles.ReplacementDecision
import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * The bridge between *selection geometry* and *renderer input*:
 *
 * ```
 * SelectionSpan / words  →  SuppressionResolver  →  Set<ShowIndex>  →  SuppressingPdfRenderer
 * ```
 *
 * It exists so [SuppressingPdfRenderer] stays completely UI-agnostic — the renderer only ever sees a
 * `Set<Int>`; it knows nothing about [TextWord], selection, the inline editor, or Compose.
 *
 * **Safety gate:** a show-op index is emitted for suppression **only** when [ReplacementAnalyzer]
 * proves the selection is exactly one self-contained operator ([ReplacementDecision.RegionReplace]).
 * If the analyzer returns [ReplacementDecision.Fallback] or [ReplacementDecision.Reject] — meaning the
 * word shares its `Tj`/`TJ` operator with neighbours, is kerned across ops, spans multiple ops, or is
 * ambiguous — the index is **withheld**, so the renderer leaves that operator drawn and no
 * neighbouring text ever disappears. Suppression is never allowed to remove more than the selected
 * word.
 *
 * Pure and read-only: it inspects an already-open [PDDocument] and mutates nothing.
 */
class SuppressionResolver(
    private val detector: PdfTextObjectDetector = PdfTextObjectDetector(),
    private val analyzer: ReplacementAnalyzer = ReplacementAnalyzer()
) {

    companion object {
        private const val TAG = "SuppressionResolver"
    }

    /**
     * Resolve the show-op indices that are SAFE to suppress for [words] on page [pageIndex]. Each
     * word is gated independently through [ReplacementAnalyzer]; only [ReplacementDecision.RegionReplace]
     * words contribute indices. Returns an empty set when nothing is safe to suppress.
     */
    fun resolve(document: PDDocument, pageIndex: Int, words: List<TextWord>): Set<Int> {
        if (words.isEmpty()) return emptySet()
        if (pageIndex < 0 || pageIndex >= document.numberOfPages) return emptySet()

        val allowed = linkedSetOf<Int>()
        for (word in words) {
            // Gate on replacement safety. newText = the word's own text so the analyzer's font-encoding
            // check always passes here — leaving the decision to hinge purely on whether the operator
            // contains ONLY this word (self-containment), which is exactly the suppression-safety test.
            val request = SelectionEditRequest(
                pageIndex = pageIndex,
                words = listOf(word),
                originalText = word.text,
                newText = word.text
            )
            val decision = analyzer.analyze(document, request)
            val suppressionAllowed = decision is ReplacementDecision.RegionReplace

            if (SuppressionDebug.ENABLED) {
                Log.d(
                    SuppressionDebug.TAG,
                    "[gate] word='${word.text}' -> decision=${decision::class.simpleName} " +
                        "suppressionAllowed=${if (suppressionAllowed) "YES" else "NO"} :: ${decision.reason}"
                )
            }

            if (suppressionAllowed) {
                detector.detectInRegion(document, pageIndex, regionOf(document, pageIndex, listOf(word)))
                    .forEach { allowed.add(it.showIndex) }
            }
        }

        if (SuppressionDebug.ENABLED) {
            Log.d(
                SuppressionDebug.TAG,
                "[resolve] page=$pageIndex words=${words.joinToString(",") { "'${it.text}'" }} " +
                    "-> allowedShowIndexes=$allowed"
            )
        }
        return allowed
    }

    /**
     * (Debug) The raw detected ops under [words] (UNGATED), so the on-page overlay can surface the
     * real glyph bbox and text-matrix-origin baseline regardless of the safety gate.
     */
    fun detect(document: PDDocument, pageIndex: Int, words: List<TextWord>): List<PdfTextObjectDetector.DetectedTextOp> {
        if (words.isEmpty()) return emptyList()
        if (pageIndex < 0 || pageIndex >= document.numberOfPages) return emptyList()
        return detector.detectInRegion(document, pageIndex, regionOf(document, pageIndex, words))
    }

    /**
     * Union of the words' normalized boxes → a PDF-point region (origin bottom-left). This mirrors
     * the coordinate mapping ReplacementAnalyzer uses; kept local so the (frozen) analyzer is not
     * touched.
     */
    private fun regionOf(document: PDDocument, pageIndex: Int, words: List<TextWord>): PdfTextObjectDetector.Region {
        val crop = document.getPage(pageIndex).cropBox
        val pw = crop.width
        val ph = crop.height
        val left = words.minOf { it.x }
        val top = words.minOf { it.y }
        val right = words.maxOf { it.x + it.width }
        val bottom = words.maxOf { it.y + it.height }
        return PdfTextObjectDetector.Region(
            left = left * pw,
            bottom = ph - bottom * ph,
            right = right * pw,
            top = ph - top * ph
        )
    }
}
