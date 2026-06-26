package com.pdf.pdfreader.feature.pdf_image.engine.transform

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.feature.pdf_image.domain.model.ImageTransform

/**
 * The single source of truth for mutating an [ImageTransform] (Chunk 4). Pure: every method
 * returns a NEW transform. No UI code, no gesture code, no StateFlow.
 */
object ImageTransformEngine {

    /**
     * Apply a drag.
     *
     * [viewportDelta] is the finger movement in VIEWPORT space — exactly what Compose
     * `positionChange()` reports for the image node. Because the renderer places position with
     * `Modifier.offset` OUTSIDE the rotation `graphicsLayer`, translation lives in viewport space,
     * so the viewport delta maps DIRECTLY onto translation and the image follows the finger at
     * every angle.
     *
     * This is the root-cause fix: the old code rotated this delta by R(θ) to "convert back from
     * local space", but the delta was never in local space — that extra rotation is precisely what
     * inverted drag at 180° and skewed it at 90°. We deliberately do NOT rotate here.
     */
    fun translate(transform: ImageTransform, viewportDelta: Offset): ImageTransform =
        transform.copy(
            translationX = transform.translationX + viewportDelta.x,
            translationY = transform.translationY + viewportDelta.y
        )

    /** Add [degrees] to rotation, normalized to [0, 360). */
    fun rotate(transform: ImageTransform, degrees: Float): ImageTransform =
        transform.copy(rotation = ((transform.rotation + degrees) % 360f + 360f) % 360f)

    /** Set a uniform scale factor. */
    fun scale(transform: ImageTransform, scale: Float): ImageTransform =
        transform.copy(scaleX = scale, scaleY = scale)
}
