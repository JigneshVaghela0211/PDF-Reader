package com.pdf.pdfreader.feature.annotation.engine

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.pdf.pdfreader.domain.model.PdfAnnotation
import javax.inject.Inject

/**
 * Builds text-markup annotations (highlight / underline / strikethrough) from a chosen
 * color, applying a type-appropriate alpha policy so results stay legible in external
 * viewers (Adobe/Foxit/Xodo/Chrome). Pure and stateless — extracted from
 * [com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel] to keep that file below the
 * God-class threshold. The color is later written into a real `PDAnnotationTextMarkup`
 * (`/C` + `/CA`) by `PdfExportManager`, so what we set here is what those viewers render.
 */
class PdfMarkupEngine @Inject constructor() {

    /**
     * Apply the per-type alpha policy to the user's base color:
     * - Highlight must stay translucent or it hides the text — a fully-opaque pick is
     *   softened to [DEFAULT_HIGHLIGHT_ALPHA]; a deliberately chosen opacity is honored.
     * - Underline / strikethrough are lines, so they want to be opaque — an accidentally
     *   near-transparent pick is bumped back to solid.
     */
    fun resolveColor(type: PdfAnnotation.MarkupType, base: Color): Color = when (type) {
        PdfAnnotation.MarkupType.HIGHLIGHT ->
            if (base.alpha >= 0.99f) base.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA) else base
        PdfAnnotation.MarkupType.UNDERLINE,
        PdfAnnotation.MarkupType.STRIKETHROUGH ->
            if (base.alpha < MIN_LINE_ALPHA) base.copy(alpha = 1f) else base
    }

    fun build(
        pageIndex: Int,
        rects: List<Rect>,
        type: PdfAnnotation.MarkupType,
        base: Color
    ): PdfAnnotation.TextMarkup = PdfAnnotation.TextMarkup(
        pageIndex = pageIndex,
        rects = rects,
        color = resolveColor(type, base),
        type = type
    )

    companion object {
        const val DEFAULT_HIGHLIGHT_ALPHA = 0.4f
        private const val MIN_LINE_ALPHA = 0.3f
    }
}
