package com.pdf.pdfreader.feature.reader.data.engine

import android.graphics.Bitmap
import android.util.Log
import com.pdf.pdfreader.domain.model.TextWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * A page's temporary **edit bitmap**: the source page re-rendered with the edited words' original
 * glyphs suppressed at the renderer level (no cover, no patch). It is displayed *in place of* — but
 * never replacing — the reader's cached normal bitmap while editing; discard it and the untouched
 * normal bitmap shows again.
 */
data class EditRenderState(
    val pageIndex: Int,
    val bitmap: Bitmap,
    /** (Debug D1) suppression diagnostics for logging + the on-page overlay; null in release/off. */
    val debug: SuppressionDebugData? = null
)

/**
 * Produces an [EditRenderState] for the page being edited by wiring the isolated pieces together:
 *
 * ```
 * words → SuppressionResolver → Set<ShowIndex> → SuppressingPdfRenderer → EditBitmap
 * ```
 *
 * This keeps the ViewModel thin: all the "load the doc, resolve the ops, render the page" work lives
 * here, not in [com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel]. It performs **no** PDF mutation,
 * **no** replacement and **no** export — it only rasterizes a bitmap.
 *
 * PDFBox is not concurrency-safe here, so [render] is blocking and must be invoked on the serialized
 * PDFBox IO dispatcher by the caller.
 */
class EditPreviewRenderer(
    private val resolver: SuppressionResolver = SuppressionResolver()
) {

    companion object {
        private const val TAG = "EditPreviewRenderer"
    }

    /**
     * Render page [pageIndex] of [path] with the glyphs of [words] suppressed, sized to
     * [targetWidthPx] wide so it aligns with the reader's width-based native bitmap. Returns `null`
     * when there is nothing to suppress or on any failure (caller keeps showing the normal bitmap).
     */
    fun render(path: String, pageIndex: Int, words: List<TextWord>, targetWidthPx: Int): EditRenderState? {
        if (path.isBlank() || words.isEmpty() || targetWidthPx <= 0) return null
        return try {
            PDDocument.load(File(path)).use { doc ->
                val skip = resolver.resolve(doc, pageIndex, words)
                if (skip.isEmpty()) {
                    Log.d(TAG, "No show-ops resolved for ${words.size} word(s) on page $pageIndex")
                    return null
                }
                val renderer = SuppressingPdfRenderer(doc, skip)
                val bitmap = renderer.renderSuppressed(pageIndex, targetWidthPx) ?: return null

                val debug = if (SuppressionDebug.ENABLED) buildDebug(doc, pageIndex, words, skip, renderer) else null
                if (SuppressionDebug.ENABLED) {
                    Log.d(
                        SuppressionDebug.TAG,
                        "[render] page=$pageIndex requested=$skip skipped=${renderer.skippedActual} " +
                            "totalOps=${renderer.totalOpsWalked} bitmap=${bitmap.width}x${bitmap.height}"
                    )
                    if (renderer.skippedActual != skip) {
                        Log.w(
                            SuppressionDebug.TAG,
                            "[render] MISMATCH requested=$skip but skipped=${renderer.skippedActual} " +
                                "— detector/drawer op indices out of lock-step; original glyphs may remain."
                        )
                    }
                }
                EditRenderState(pageIndex, bitmap, debug)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Edit-preview render failed on page $pageIndex", e)
            null
        }
    }

    /** (Debug D1) Map the resolved ops to normalized geometry + true baseline for the overlay. */
    private fun buildDebug(
        doc: PDDocument,
        pageIndex: Int,
        words: List<TextWord>,
        skip: Set<Int>,
        renderer: SuppressingPdfRenderer
    ): SuppressionDebugData {
        val crop = doc.getPage(pageIndex).cropBox
        val pw = crop.width
        val ph = crop.height
        val ops = resolver.detect(doc, pageIndex, words).map { op ->
            DebugWordOp(
                showIndex = op.showIndex,
                text = op.text,
                leftN = op.left / pw,
                topN = (ph - op.top) / ph,
                rightN = op.right / pw,
                bottomN = (ph - op.bottom) / ph,
                baselineN = (ph - op.renderMatrix.translateY) / ph
            )
        }
        return SuppressionDebugData(
            requested = skip.sorted(),
            skipped = renderer.skippedActual.sorted(),
            totalOps = renderer.totalOpsWalked,
            ops = ops
        )
    }
}
