package com.pdf.pdfreader.feature.reader.data.engine

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.PageDrawer
import com.tom_roush.pdfbox.rendering.PageDrawerParameters
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector

/**
 * Renderer-based text suppression (the approved editing-preview mechanism).
 *
 * Instead of *painting over* original glyphs (white box / sampled background patch — permanently
 * rejected), this renders the page through PDFBox-Android's hookable [PDFRenderer] while a custom
 * [PageDrawer] simply **never draws** the targeted show-text operators. Everything else on the page
 * (rules, backgrounds, images, other text) draws normally, so the selected word's slot is genuinely
 * empty — no cover required.
 *
 * Targets are named by **show-operator index**, assigned in content-stream order exactly like
 * [com.pdf.pdfreader.utiles.PdfTextObjectDetector.OpCollector] and [PdfContentStreamEditor]: one per
 * `Tj`/`'`/`"` and one per `COSString` segment inside a `TJ`. Because [PdfTextObjectDetector]
 * (a `PDFTextStripper`) and this [PageDrawer] both walk the same [PDFStreamEngine] operator stream,
 * their indices stay in lock-step — so a `showIndex` produced by `detectInRegion` maps 1:1 onto the
 * op this drawer skips.
 *
 * This class performs **no** PDF mutation and executes **no** replacement — it only renders a
 * bitmap. It is not concurrency-safe (PDFBox constraint): callers must run [renderSuppressed] /
 * [renderImage] on the serialized PDFBox IO dispatcher, as with the other extractors.
 *
 * @param document the loaded source document (never modified).
 * @param skipShowIndexes the set of content-stream show-op indices whose glyphs must not be painted.
 */
class SuppressingPdfRenderer(
    private val document: PDDocument,
    private val skipShowIndexes: Set<Int>
) : PDFRenderer(document) {

    /** (Debug D1) ShowIndexes the drawer actually skipped during the last render. */
    val skippedActual: MutableSet<Int> = linkedSetOf()
    /** (Debug D1) Total show-text ops the drawer walked during the last render. */
    var totalOpsWalked: Int = 0
        private set

    override fun createPageDrawer(parameters: PageDrawerParameters): PageDrawer =
        SuppressingPageDrawer(
            parameters, skipShowIndexes,
            skippedSink = skippedActual,
            onOpWalked = { totalOpsWalked = it }
        )

    /**
     * Render page [pageIndex] (0-based) to an ARGB bitmap [targetWidthPx] pixels wide — matching the
     * reader's width-based native bitmap so existing normalized word coordinates keep mapping — with
     * the [skipShowIndexes] operators absent. Returns `null` if the page has no usable width.
     */
    fun renderSuppressed(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val page = document.getPage(pageIndex)
        val cropWidth = page.cropBox?.width?.takeIf { it > 0f }
            ?: page.mediaBox?.width?.takeIf { it > 0f }
            ?: return null
        val scale = targetWidthPx / cropWidth
        return renderImage(pageIndex, scale, ImageType.ARGB)
    }
}

/**
 * A [PageDrawer] that paints the page normally except for the show-text operators whose
 * content-stream index is in [skip], which it silently drops.
 *
 * Counting mirrors [com.pdf.pdfreader.utiles.PdfTextObjectDetector.OpCollector]: [showText] runs
 * once per `Tj`/`'`/`"` and once per `COSString` in a `TJ`, so [opIndex] advances in lock-step with
 * the detector's `showIndex`. The 4-arg [showGlyph] is the render funnel (it dispatches to both
 * font and Type3 glyph paint), so gating it suppresses every glyph of the current op.
 */
private class SuppressingPageDrawer(
    parameters: PageDrawerParameters,
    private val skip: Set<Int>,
    private val skippedSink: MutableSet<Int>,
    private val onOpWalked: (Int) -> Unit
) : PageDrawer(parameters) {

    private var opIndex = 0
    private var suppressCurrent = false

    override fun showText(string: ByteArray) {
        suppressCurrent = opIndex in skip
        if (suppressCurrent) skippedSink.add(opIndex)
        super.showText(string)
        opIndex++
        onOpWalked(opIndex)
    }

    override fun showGlyph(
        textRenderingMatrix: Matrix,
        font: PDFont,
        code: Int,
        displacement: Vector
    ) {
        if (suppressCurrent) return
        super.showGlyph(textRenderingMatrix, font, code, displacement)
    }
}
