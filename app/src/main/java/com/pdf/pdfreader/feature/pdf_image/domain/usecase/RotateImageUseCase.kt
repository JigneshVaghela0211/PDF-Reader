package com.pdf.pdfreader.feature.pdf_image.domain.usecase

import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.feature.pdf_image.domain.model.ImageTransform
import com.pdf.pdfreader.feature.pdf_image.engine.gesture.ImageGesture
import com.pdf.pdfreader.feature.pdf_image.engine.gesture.ImageGestureProcessor

/**
 * Computes an image's new rotation (degrees, normalized to [0, 360)) after rotating by [degrees].
 * Keeps rotation math out of the ViewModel (Chunk 6).
 */
object RotateImageUseCase {
    operator fun invoke(element: ImageElement, degrees: Float): Float =
        ImageGestureProcessor.process(
            ImageTransform.from(element),
            ImageGesture.Rotate(degrees)
        ).rotation
}
