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

    data class TextState(
        val id: String,
        val text: String,
        val color: Long,
        val fontSize: Float,
        val positionX: Float,
        val positionY: Float
    )

    data class TextCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val before: TextState?,
        val after: TextState
    ) : AnnotationCommand()

    // ─── PDF Text Edit Commands ─────────────────────────────────

    /**
     * Snapshot of an edited text block for undo/redo.
     */
    data class EditTextState(
        val blockId: String,
        val originalText: String,
        val newText: String,
        val originalFontSize: Float,
        val newFontSize: Float,
        val newColor: Long,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    /**
     * Command for editing existing PDF text.
     */
    data class EditTextCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val before: EditTextState?,
        val after: EditTextState
    ) : AnnotationCommand()

    // ─── Image Commands ─────────────────────────────────────────

    /**
     * Snapshot of an image element state for undo/redo.
     */
    data class ImageState(
        val elementId: String,
        val uri: String,
        val positionX: Float,
        val positionY: Float,
        val width: Float,
        val height: Float,
        val scale: Float,
        val rotation: Float
    )

    /**
     * Command for adding an image to a PDF page.
     */
    data class AddImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val imageState: ImageState
    ) : AnnotationCommand()

    /**
     * Command for moving an image on a PDF page.
     */
    data class MoveImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeX: Float,
        val beforeY: Float,
        val afterX: Float,
        val afterY: Float
    ) : AnnotationCommand()

    /**
     * Command for resizing an image.
     */
    data class ResizeImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeWidth: Float,
        val beforeHeight: Float,
        val afterWidth: Float,
        val afterHeight: Float,
        val beforeX: Float,
        val beforeY: Float,
        val afterX: Float,
        val afterY: Float
    ) : AnnotationCommand()

    /**
     * Command for rotating an image.
     */
    data class RotateImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeRotation: Float,
        val afterRotation: Float
    ) : AnnotationCommand()

    /**
     * Command for deleting an image from a PDF page.
     * Stores full state so the image can be restored on undo.
     */
    data class DeleteImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val deletedImageState: ImageState
    ) : AnnotationCommand()
}

/**
 * Lightweight serializable offset without Compose dependency.
 */
data class SerializableOffset(val x: Float, val y: Float)
