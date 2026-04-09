package com.pdf.pdfreader.domain.model

import androidx.compose.ui.geometry.Offset

/**
 * Represents an image inserted by the user onto a PDF page.
 * All positions and sizes are in view-pixel coordinates (same space as annotations).
 */
data class ImageElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    /** Content URI string of the source image */
    val uri: String,
    /** Top-left position in view coordinates */
    val position: Offset,
    /** Width in view pixels */
    val width: Float,
    /** Height in view pixels */
    val height: Float,
    /** Uniform scale factor (1.0 = original) */
    val scale: Float = 1f,
    /** Rotation in degrees (0, 90, 180, 270) */
    val rotation: Float = 0f,
    /** Opacity from 0f (transparent) to 1f (fully opaque) */
    val opacity: Float = 1f,
    /** When true, element cannot be moved or resized */
    val isLocked: Boolean = false,
    /** Layer ordering index. Higher values render on top. */
    val zIndex: Int = 0
)

/**
 * Identifies which resize handle the user is interacting with.
 */
enum class ResizeHandle {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    LEFT_CENTER,
    RIGHT_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}
