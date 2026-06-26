package com.pdf.pdfreader.feature.pdf_image.engine.hit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.feature.pdf_image.engine.matrix.ImageMatrixCalculator

/**
 * Pure hit-testing for a (possibly rotated) image. A viewport point is inside the image when,
 * rotated back around the image center by −rotation, it lands within the un-rotated bounds.
 * Stateless; uses [ImageMatrixCalculator] for the rotation so no trig is duplicated.
 */
object ImageHitTester {

    /** True if [viewportPoint] (viewport space) lies within [element]'s rotated bounds. */
    fun contains(viewportPoint: Offset, element: ImageElement): Boolean {
        val w = element.width * element.scale
        val h = element.height * element.scale
        val center = Offset(element.position.x + w / 2f, element.position.y + h / 2f)
        val local = ImageMatrixCalculator.rotatePointAround(viewportPoint, center, -element.rotation)
        return Rect(
            element.position.x, element.position.y,
            element.position.x + w, element.position.y + h
        ).contains(local)
    }

    /** Topmost (highest zIndex) image under [viewportPoint], or null. */
    fun topMostAt(viewportPoint: Offset, elements: List<ImageElement>): ImageElement? =
        elements.filter { contains(viewportPoint, it) }.maxByOrNull { it.zIndex }
}
