package com.pdf.pdfreader.domain.model

import androidx.compose.ui.geometry.Offset

/**
 * Serializable stroke data for preserving editable signature vector data.
 * Uses Long for color (instead of Compose Color) to keep domain model framework-free.
 */
data class SerializableStroke(
    val points: List<SerializableOffset>,
    val color: Long,
    val strokeWidth: Float
) {
    companion object {
        fun fromComposeStroke(stroke: com.pdf.pdfreader.ui.components.SignatureStroke): SerializableStroke {
            return SerializableStroke(
                points = stroke.points.map { SerializableOffset(it.x, it.y) },
                color = stroke.color.value.toLong(),
                strokeWidth = stroke.strokeWidth
            )
        }
    }

    fun toComposeStroke(): com.pdf.pdfreader.ui.components.SignatureStroke {
        return com.pdf.pdfreader.ui.components.SignatureStroke(
            points = points.map { androidx.compose.ui.geometry.Offset(it.x, it.y) },
            color = androidx.compose.ui.graphics.Color(color.toULong()),
            strokeWidth = strokeWidth
        )
    }
}

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
    val zIndex: Int = 0,
    
    /** Elements with the same groupId move, scale, and rotate together */
    val groupId: String? = null,
    
    /** True if this element is a signature (enables signature-specific editing) */
    val isSignature: Boolean = false,
    /** Preserved stroke data for re-rendering after thickness/color edits */
    val signatureStrokes: List<SerializableStroke>? = null,
    /** Canvas dimensions used when the signature was originally drawn */
    val signatureCanvasWidth: Float = 0f,
    val signatureCanvasHeight: Float = 0f,
    /** Incremented on each re-render to bust bitmap cache */
    val bitmapVersion: Int = 0,
    
    // --- TRANSIENT RESIZE STATE (For State-driven, continuous layout-free resize) ---
    val isResizing: Boolean = false,
    val transientScaleX: Float = 1f,
    val transientScaleY: Float = 1f,
    val transientTransformOrigin: androidx.compose.ui.graphics.TransformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
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
