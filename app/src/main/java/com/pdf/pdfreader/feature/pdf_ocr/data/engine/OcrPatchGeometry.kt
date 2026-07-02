package com.pdf.pdfreader.feature.pdf_ocr.data.engine

/**
 * Pure geometry for the OCR-edit "cover patch": grows a recognized word's box so the
 * background-colored fill fully hides the original scanned glyph before the replacement
 * text is drawn on top.
 *
 * ML Kit element boxes are tight to the visible ink, so a fixed 1pt inflation leaves the
 * original word's anti-alias halo and any ascenders/descenders peeking around the patch —
 * that is the "old word still visible" overlap. We therefore inflate **proportionally**:
 *
 * - **Vertical** generously (tall glyphs / halo commonly exceed the reported box height).
 * - **Horizontal** modestly, so the patch never swallows the neighbouring word on the line.
 *
 * Kept free of Android/PDFBox types so it stays JVM-unit-testable alongside [OcrCoordinateMapper].
 */
object OcrPatchGeometry {

    private const val VERTICAL_INFLATE_FRACTION = 0.30f
    private const val HORIZONTAL_INFLATE_FRACTION = 0.06f
    private const val MIN_VERTICAL_PT = 2f
    private const val MIN_HORIZONTAL_PT = 1.5f

    /** Inflate a user-space word rect into the patch rect that covers the original scan. */
    fun inflate(rect: OcrCoordinateMapper.PdfRect): OcrCoordinateMapper.PdfRect {
        val padX = maxOf(rect.width * HORIZONTAL_INFLATE_FRACTION, MIN_HORIZONTAL_PT)
        val padY = maxOf(rect.height * VERTICAL_INFLATE_FRACTION, MIN_VERTICAL_PT)
        return OcrCoordinateMapper.PdfRect(
            x = rect.x - padX,
            y = rect.y - padY,
            width = rect.width + 2 * padX,
            height = rect.height + 2 * padY
        )
    }

    /** Fractions exposed so the on-screen preview patch can match the exported one. */
    fun verticalPadPx(heightPx: Float): Float = maxOf(heightPx * VERTICAL_INFLATE_FRACTION, MIN_VERTICAL_PT)
    fun horizontalPadPx(widthPx: Float): Float = maxOf(widthPx * HORIZONTAL_INFLATE_FRACTION, MIN_HORIZONTAL_PT)
}
