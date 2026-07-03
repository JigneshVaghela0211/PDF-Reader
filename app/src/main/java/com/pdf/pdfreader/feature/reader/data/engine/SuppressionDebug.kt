package com.pdf.pdfreader.feature.reader.data.engine

/**
 * TEMPORARY (Debug Chunk D1) instrumentation for renderer text-suppression.
 *
 * Everything gated by [ENABLED] / [VISUAL] is diagnostic only and must be removed once suppression
 * is verified — it does not affect replacement, export, OCR or save. Flip [ENABLED] to false to mute
 * the logs; [VISUAL] to false to hide the on-page baseline/bbox overlay.
 */
object SuppressionDebug {
    const val ENABLED = true
    const val VISUAL = true
    const val TAG = "SuppressDbg"
}

/**
 * Per-render suppression diagnostics captured through the pipeline
 * (resolve → showIndex → render → skip), surfaced to the UI for logging and the visual overlay.
 */
data class SuppressionDebugData(
    /** ShowIndexes the resolver asked to suppress. */
    val requested: List<Int>,
    /** ShowIndexes the PageDrawer actually skipped while rendering. */
    val skipped: List<Int>,
    /** Total show-text ops the drawer walked on the page (upper bound of valid indices). */
    val totalOps: Int,
    /** Geometry of the resolved ops for the on-page overlay (normalized, top-left origin). */
    val ops: List<DebugWordOp>
)

/**
 * One detected text operator's geometry in normalized (0..1) top-left space, so the overlay can draw
 * the real glyph bbox and — crucially — the true **PDF baseline** (from the text matrix origin), to
 * show exactly how far the bbox-anchored TextField sits from where the glyphs actually rest.
 */
data class DebugWordOp(
    val showIndex: Int,
    val text: String,
    val leftN: Float,
    val topN: Float,
    val rightN: Float,
    val bottomN: Float,
    /** Normalized y (from top) of the text-rendering-matrix origin = the PDF baseline. */
    val baselineN: Float
)
