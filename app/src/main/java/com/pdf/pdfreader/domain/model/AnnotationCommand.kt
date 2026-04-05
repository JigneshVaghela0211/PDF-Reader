package com.pdf.pdfreader.domain.model

/**
 * Domain-level sealed class representing annotation commands for undo/redo.
 *
 * All data is lightweight — coordinates, colors (as Long), text, stroke widths.
 * NO bitmaps or large objects are stored.
 */
sealed class AnnotationCommand {
    abstract val id: String
    abstract val pdfPath: String
    abstract val pageIndex: Int
    abstract val timestamp: Long

    /**
     * Command for adding a path annotation (pen stroke / highlighter).
     */
    data class AddPath(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val annotationId: String,
        val points: List<SerializableOffset>,
        val color: Long,
        val strokeWidth: Float,
        val isHighlighter: Boolean
    ) : AnnotationCommand()

    /**
     * Command for adding a text note annotation.
     */
    data class AddTextNote(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val annotationId: String,
        val text: String,
        val positionX: Float,
        val positionY: Float,
        val color: Long,
        val fontSize: Float
    ) : AnnotationCommand()

    /**
     * Command for removing an annotation.
     * Stores a JSON snapshot of the removed annotation so it can be re-added on undo.
     */
    data class RemoveAnnotation(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val annotationId: String,
        val removedAnnotationPayload: String
    ) : AnnotationCommand()

    /**
     * Command for updating an annotation (e.g., moving a text note, editing text).
     * Stores both previous and new state snapshots.
     */
    data class UpdateAnnotation(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val annotationId: String,
        val previousPayload: String,
        val newPayload: String
    ) : AnnotationCommand()
}

/**
 * Lightweight serializable offset without Compose dependency.
 */
data class SerializableOffset(val x: Float, val y: Float)
