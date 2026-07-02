package com.pdf.pdfreader.feature.pdf_ocr.data.engine

/**
 * Pure coordinate math between the three OCR spaces:
 *
 * 1. **Bitmap px** — the rendered page bitmap ML Kit sees. Top-left origin, y down.
 *    `PdfRenderer` applies the page's /Rotate, so this is the page *as displayed*.
 * 2. **Normalized display** — bitmap px ÷ bitmap size (0..1). What OcrWord stores.
 * 3. **PDF user space** — PDFBox content-stream coordinates. Bottom-left origin,
 *    y up, and **unrotated**: the crop box ignores /Rotate.
 *
 * Mapping display → user space therefore needs the inverse of the /Rotate the
 * renderer applied. The returned [PdfTextAnchor] also carries the rotation the
 * text matrix must apply so glyphs written in user space come out horizontal on
 * the displayed page (fixes the old engine's "no /Rotate support" limitation).
 *
 * Deliberately free of Android/PDFBox types so it stays JVM-unit-testable.
 */
object OcrCoordinateMapper {

    /** Word box in normalized display space (top-left origin, 0..1). */
    data class NormalizedRect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    /**
     * Where and how to write a word into the PDF content stream:
     * baseline origin in user-space points and the CCW rotation (degrees) for the
     * text matrix — pass to `Matrix.getRotateInstance(toRadians(rotation), x, y)`.
     */
    data class PdfTextAnchor(
        val x: Float,
        val y: Float,
        val rotationDegrees: Int,
        val fontSize: Float
    )

    fun toNormalized(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): NormalizedRect = NormalizedRect(
        x = left / bitmapWidth.toFloat(),
        y = top / bitmapHeight.toFloat(),
        width = (right - left) / bitmapWidth.toFloat(),
        height = (bottom - top) / bitmapHeight.toFloat()
    )

    /** Page size in points as displayed (crop box swapped for 90/270 rotations). */
    fun displayedSize(cropWidth: Float, cropHeight: Float, rotation: Int): Pair<Float, Float> =
        if (normalizeRotation(rotation) % 180 == 90) cropHeight to cropWidth
        else cropWidth to cropHeight

    /**
     * Map a normalized display-space word box to its user-space baseline anchor.
     * The baseline is the box's bottom-left corner in display space; the font size
     * is the box height in display points (physical glyph height).
     */
    fun toPdfAnchor(
        rect: NormalizedRect,
        cropWidth: Float,
        cropHeight: Float,
        cropOffsetX: Float,
        cropOffsetY: Float,
        rotation: Int
    ): PdfTextAnchor {
        val rot = normalizeRotation(rotation)
        val (dispW, dispH) = displayedSize(cropWidth, cropHeight, rot)

        // Baseline = bottom-left of the word box, converted to display coords with a
        // bottom-left origin (y up) so the rotation math below is plain geometry.
        val dx = rect.x * dispW
        val dy = dispH - (rect.y + rect.height) * dispH

        // Invert the clockwise display rotation to land back in unrotated user space.
        val (ux, uy) = displayToUser(dx, dy, cropWidth, cropHeight, rot)

        return PdfTextAnchor(
            x = ux + cropOffsetX,
            y = uy + cropOffsetY,
            rotationDegrees = rot,
            fontSize = (rect.height * dispH).coerceIn(1f, 300f)
        )
    }

    /** Word box in PDF user-space points (bottom-left origin). */
    data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * Map a normalized display rect to user space. Rotations are multiples of 90°,
     * so the mapped rect stays axis-aligned: map two opposite corners and take the
     * min/max.
     */
    fun toPdfRect(
        rect: NormalizedRect,
        cropWidth: Float,
        cropHeight: Float,
        cropOffsetX: Float,
        cropOffsetY: Float,
        rotation: Int
    ): PdfRect {
        val rot = normalizeRotation(rotation)
        val (dispW, dispH) = displayedSize(cropWidth, cropHeight, rot)

        // Corners in display coords with bottom-left origin (y up).
        val ax = rect.x * dispW
        val ay = dispH - rect.y * dispH
        val bx = (rect.x + rect.width) * dispW
        val by = dispH - (rect.y + rect.height) * dispH

        val (ux1, uy1) = displayToUser(ax, ay, cropWidth, cropHeight, rot)
        val (ux2, uy2) = displayToUser(bx, by, cropWidth, cropHeight, rot)

        return PdfRect(
            x = minOf(ux1, ux2) + cropOffsetX,
            y = minOf(uy1, uy2) + cropOffsetY,
            width = kotlin.math.abs(ux1 - ux2),
            height = kotlin.math.abs(uy1 - uy2)
        )
    }

    /** Invert the clockwise display rotation for one point (bottom-left origin math coords). */
    private fun displayToUser(
        dx: Float,
        dy: Float,
        cropWidth: Float,
        cropHeight: Float,
        rotation: Int
    ): Pair<Float, Float> = when (rotation) {
        90 -> cropWidth - dy to dx
        180 -> cropWidth - dx to cropHeight - dy
        270 -> dy to cropHeight - dx
        else -> dx to dy
    }

    private fun normalizeRotation(rotation: Int): Int = ((rotation % 360) + 360) % 360
}
