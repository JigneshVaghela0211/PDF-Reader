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
     * Command for adding a text-markup annotation (highlight / underline / strikeout).
     * Rects are normalized (0..1) selection rects; [markupType] is a [PdfAnnotation.MarkupType] name.
     */
    data class AddMarkupCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val annotationId: String,
        val rects: List<SerializableRect>,
        val color: Long,
        val markupType: String
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
        val positionY: Float,
        /** Rotation in degrees (clockwise on screen). Default 0 for back-compat with old payloads. */
        val rotation: Float = 0f
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
        val height: Float,
        val opacity: Float = 1f,
        val isLocked: Boolean = false,
        val alignment: String = "LEFT"
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
        val rotation: Float,
        val opacity: Float = 1f,
        val isLocked: Boolean = false,
        val zIndex: Int = 0
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

    // ─── Duplicate Commands ─────────────────────────────────────

    /**
     * Command for duplicating an image element.
     */
    data class DuplicateImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val duplicatedImageState: ImageState
    ) : AnnotationCommand()

    // ─── Layer Commands ─────────────────────────────────────────

    /**
     * Command for changing the layer order of an image element.
     */
    data class ChangeLayerCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeZIndex: Int,
        val afterZIndex: Int
    ) : AnnotationCommand()

    // ─── Opacity Commands ───────────────────────────────────────

    /**
     * Command for changing the opacity of an image element.
     */
    data class ChangeImageOpacityCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeOpacity: Float,
        val afterOpacity: Float
    ) : AnnotationCommand()

    // ─── Lock Commands ──────────────────────────────────────────

    /**
     * Command for toggling the lock state of an image element.
     */
    data class LockImageCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val elementId: String,
        val beforeLocked: Boolean,
        val afterLocked: Boolean
    ) : AnnotationCommand()

    /**
     * A composite command that groups multiple commands into a single undo/redo transaction.
     * Useful for performing simultaneous actions on a group of synchronized elements.
     */
    data class CompositeCommand(
        override val id: String,
        override val pdfPath: String,
        override val pageIndex: Int,
        override val timestamp: Long,
        val commands: List<AnnotationCommand>
    ) : AnnotationCommand()
}

/**
 * Lightweight serializable offset without Compose dependency.
 */
data class SerializableOffset(val x: Float, val y: Float)

/**
 * Lightweight serializable rectangle (normalized PDF-space coords) without Compose dependency.
 */
data class SerializableRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

