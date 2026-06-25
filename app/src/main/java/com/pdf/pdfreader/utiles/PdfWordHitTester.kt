package com.pdf.pdfreader.utiles

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.domain.model.TextWord

/**
 * Pure hit-testing for word-level text selection: maps a touch point on a rendered page to the
 * [TextWord] under it. Stateless — kept out of the Composable so the gesture layer stays thin.
 *
 * Word coordinates are normalized (0..1); the page pixel size is supplied per call so the same
 * words work at any render scale.
 */
object PdfWordHitTester {

    /** Extra tap tolerance in screen pixels, so small words remain easy to hit. */
    private const val DEFAULT_PADDING_PX = 12f

    fun wordAt(
        offset: Offset,
        pageWidth: Int,
        pageHeight: Int,
        words: List<TextWord>,
        paddingPx: Float = DEFAULT_PADDING_PX
    ): TextWord? {
        if (pageWidth <= 0 || pageHeight <= 0) return null
        val normX = offset.x / pageWidth
        val normY = offset.y / pageHeight
        val padX = paddingPx / pageWidth
        val padY = paddingPx / pageHeight

        return words.firstOrNull { w ->
            normX >= w.x - padX && normX <= w.x + w.width + padX &&
                normY >= w.y - padY && normY <= w.y + w.height + padY
        }
    }
}
