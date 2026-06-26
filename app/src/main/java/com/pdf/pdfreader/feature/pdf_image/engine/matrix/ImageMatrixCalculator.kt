package com.pdf.pdfreader.feature.pdf_image.engine.matrix

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * The single place that does image rotation trig. No ViewModel or Composable should compute
 * sin/cos by hand (Chunk: matrix calculations centralized here).
 *
 * Convention: angles are DEGREES, matching Compose `graphicsLayer.rotationZ`. Screen space is
 * y-DOWN, so a positive angle is a clockwise visual rotation. Mapping a vector from the image's
 * local space to viewport space is R(+θ); the inverse (viewport → local) is R(−θ).
 */
object ImageMatrixCalculator {

    /** Rotate [v] by [degrees] (local → viewport, R(+θ)). Identity at multiples of 360°. */
    fun rotateVector(v: Offset, degrees: Float): Offset {
        if (degrees % 360f == 0f) return v
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    /** Rotate [v] by −[degrees] (viewport → local, R(−θ)). */
    fun inverseRotateVector(v: Offset, degrees: Float): Offset = rotateVector(v, -degrees)

    /** Rotate [point] around [pivot] by [degrees]. Used to hit-test rotated image bounds. */
    fun rotatePointAround(point: Offset, pivot: Offset, degrees: Float): Offset =
        pivot + rotateVector(point - pivot, degrees)
}
