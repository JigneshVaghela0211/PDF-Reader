package com.pdf.pdfreader.feature.pdf_image.engine.gesture

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.feature.pdf_image.domain.model.ImageTransform
import com.pdf.pdfreader.feature.pdf_image.engine.transform.ImageTransformEngine

/** A raw gesture the user performed on an image. All deltas are in VIEWPORT space. */
sealed interface ImageGesture {
    /** Finger drag by [viewportDelta] (viewport space). */
    data class Drag(val viewportDelta: Offset) : ImageGesture
    /** Rotate by [degrees] (relative). */
    data class Rotate(val degrees: Float) : ImageGesture
}

/**
 * Turns a raw [ImageGesture] into an updated [ImageTransform] by delegating to
 * [ImageTransformEngine] (Chunk 5). Stateless; returns only a transform update and never mutates
 * UI/ViewModel state itself.
 *
 * Resize is intentionally NOT routed through here: the existing handle-anchored, aspect-ratio
 * resize already works and lives in the ViewModel — folding it in would rewrite working code,
 * which the brief forbids.
 */
object ImageGestureProcessor {
    fun process(transform: ImageTransform, gesture: ImageGesture): ImageTransform =
        when (gesture) {
            is ImageGesture.Drag -> ImageTransformEngine.translate(transform, gesture.viewportDelta)
            is ImageGesture.Rotate -> ImageTransformEngine.rotate(transform, gesture.degrees)
        }
}
