package com.pdf.pdfreader.feature.pdf_image.domain.usecase

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.feature.pdf_image.domain.model.ImageTransform
import com.pdf.pdfreader.feature.pdf_image.engine.gesture.ImageGesture
import com.pdf.pdfreader.feature.pdf_image.engine.gesture.ImageGestureProcessor

/**
 * Computes an image's new VIEWPORT position after a drag (Chunk 6). Keeps all transform/rotation
 * math out of the ViewModel — the ViewModel just calls this and owns clamping/grouping policy.
 *
 * @return the element's new top-left position (un-clamped) in viewport space.
 */
object UpdateImagePositionUseCase {
    operator fun invoke(element: ImageElement, viewportDelta: Offset): Offset {
        val updated = ImageGestureProcessor.process(
            ImageTransform.from(element),
            ImageGesture.Drag(viewportDelta)
        )
        return Offset(updated.translationX, updated.translationY)
    }
}
