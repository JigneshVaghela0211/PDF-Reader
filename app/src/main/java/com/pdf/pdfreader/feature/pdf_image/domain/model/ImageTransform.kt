package com.pdf.pdfreader.feature.pdf_image.domain.model

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.domain.model.ImageElement

/**
 * Decomposed transform for a single image, kept as discrete values (Chunk 3) so no caller has to
 * juggle a raw matrix.
 *
 * IMPORTANT coordinate-space note: the renderer applies position via `Modifier.offset` placed
 * OUTSIDE the rotation `graphicsLayer` (see GlobalImageOverlay), so [translationX]/[translationY]
 * are in VIEWPORT space — NOT local/pre-rotation space. [pivotX]/[pivotY] are the element-local
 * center used by `graphicsLayer` (TransformOrigin.Center) and by hit-testing.
 */
data class ImageTransform(
    val translationX: Float,
    val translationY: Float,
    val rotation: Float,   // degrees, matches graphicsLayer.rotationZ
    val scaleX: Float,
    val scaleY: Float,
    val pivotX: Float,
    val pivotY: Float
) {
    val translation: Offset get() = Offset(translationX, translationY)

    companion object {
        /** Decompose an [ImageElement] into its transform. */
        fun from(element: ImageElement): ImageTransform {
            val w = element.width * element.scale
            val h = element.height * element.scale
            return ImageTransform(
                translationX = element.position.x,
                translationY = element.position.y,
                rotation = element.rotation,
                scaleX = element.scale,
                scaleY = element.scale,
                pivotX = w / 2f,
                pivotY = h / 2f
            )
        }
    }
}
