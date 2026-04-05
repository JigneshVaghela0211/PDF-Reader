package com.pdf.pdfreader.data.local

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.pdf.pdfreader.domain.model.AnnotationCommand
import com.pdf.pdfreader.domain.model.SerializableOffset

/**
 * Utility for converting [AnnotationCommand] to/from Room-storable format.
 *
 * Stores ONLY lightweight data:
 * ✅ Coordinates, paths, text, colors (as Long), stroke widths, font sizes
 * ❌ NO bitmaps, NO large objects
 */
object CommandSerializer {

    private val gson = Gson()

    // ─── Command Type Constants ─────────────────────────────────

    const val TYPE_ADD_PATH = "ADD_PATH"
    const val TYPE_ADD_TEXT = "ADD_TEXT"
    const val TYPE_REMOVE = "REMOVE"
    const val TYPE_UPDATE = "UPDATE"
    const val TYPE_TEXT_COMMAND = "TEXT_COMMAND"

    // ─── Serialize: AnnotationCommand → (type, payload JSON) ────

    fun getType(command: AnnotationCommand): String = when (command) {
        is AnnotationCommand.AddPath -> TYPE_ADD_PATH
        is AnnotationCommand.AddTextNote -> TYPE_ADD_TEXT
        is AnnotationCommand.RemoveAnnotation -> TYPE_REMOVE
        is AnnotationCommand.UpdateAnnotation -> TYPE_UPDATE
        is AnnotationCommand.TextCommand -> TYPE_TEXT_COMMAND
    }

    fun toPayload(command: AnnotationCommand): String = when (command) {
        is AnnotationCommand.AddPath -> gson.toJson(
            AddPathPayload(
                annotationId = command.annotationId,
                points = command.points,
                color = command.color,
                strokeWidth = command.strokeWidth,
                isHighlighter = command.isHighlighter
            )
        )

        is AnnotationCommand.AddTextNote -> gson.toJson(
            AddTextPayload(
                annotationId = command.annotationId,
                text = command.text,
                positionX = command.positionX,
                positionY = command.positionY,
                color = command.color,
                fontSize = command.fontSize
            )
        )

        is AnnotationCommand.RemoveAnnotation -> gson.toJson(
            RemovePayload(
                annotationId = command.annotationId,
                removedAnnotationPayload = command.removedAnnotationPayload
            )
        )

        is AnnotationCommand.UpdateAnnotation -> gson.toJson(
            UpdatePayload(
                annotationId = command.annotationId,
                previousPayload = command.previousPayload,
                newPayload = command.newPayload
            )
        )

        is AnnotationCommand.TextCommand -> gson.toJson(command)
    }

    // ─── Deserialize: (type, payload JSON) → AnnotationCommand ──

    fun fromEntity(entity: AnnotationCommandEntity): AnnotationCommand {
        return when (entity.type) {
            TYPE_ADD_PATH -> {
                val p = gson.fromJson(entity.payload, AddPathPayload::class.java)
                AnnotationCommand.AddPath(
                    id = entity.id,
                    pdfPath = entity.pdfPath,
                    pageIndex = entity.pageIndex,
                    timestamp = entity.createdAt,
                    annotationId = p.annotationId,
                    points = p.points,
                    color = p.color,
                    strokeWidth = p.strokeWidth,
                    isHighlighter = p.isHighlighter
                )
            }

            TYPE_ADD_TEXT -> {
                val p = gson.fromJson(entity.payload, AddTextPayload::class.java)
                AnnotationCommand.AddTextNote(
                    id = entity.id,
                    pdfPath = entity.pdfPath,
                    pageIndex = entity.pageIndex,
                    timestamp = entity.createdAt,
                    annotationId = p.annotationId,
                    text = p.text,
                    positionX = p.positionX,
                    positionY = p.positionY,
                    color = p.color,
                    fontSize = p.fontSize
                )
            }

            TYPE_REMOVE -> {
                val p = gson.fromJson(entity.payload, RemovePayload::class.java)
                AnnotationCommand.RemoveAnnotation(
                    id = entity.id,
                    pdfPath = entity.pdfPath,
                    pageIndex = entity.pageIndex,
                    timestamp = entity.createdAt,
                    annotationId = p.annotationId,
                    removedAnnotationPayload = p.removedAnnotationPayload
                )
            }

            TYPE_UPDATE -> {
                val p = gson.fromJson(entity.payload, UpdatePayload::class.java)
                AnnotationCommand.UpdateAnnotation(
                    id = entity.id,
                    pdfPath = entity.pdfPath,
                    pageIndex = entity.pageIndex,
                    timestamp = entity.createdAt,
                    annotationId = p.annotationId,
                    previousPayload = p.previousPayload,
                    newPayload = p.newPayload
                )
            }

            TYPE_TEXT_COMMAND -> {
                gson.fromJson(entity.payload, AnnotationCommand.TextCommand::class.java)
            }

            else -> throw IllegalArgumentException("Unknown command type: ${entity.type}")
        }
    }

    fun toEntity(command: AnnotationCommand): AnnotationCommandEntity {
        return AnnotationCommandEntity(
            id = command.id,
            pdfPath = command.pdfPath,
            pageIndex = command.pageIndex,
            type = getType(command),
            payload = toPayload(command),
            isUndone = false,
            createdAt = command.timestamp
        )
    }

    // ─── Annotation Serialization Helpers ────────────────────────

    /**
     * Serialize a PdfAnnotation.Path to a JSON string for storing as
     * the "removed annotation" payload in RemoveAnnotation commands.
     */
    fun serializePathAnnotation(
        annotationId: String,
        pageIndex: Int,
        points: List<SerializableOffset>,
        color: Long,
        strokeWidth: Float,
        isHighlighter: Boolean
    ): String = gson.toJson(
        AnnotationSnapshot(
            type = TYPE_ADD_PATH,
            annotationId = annotationId,
            pageIndex = pageIndex,
            pathData = AddPathPayload(annotationId, points, color, strokeWidth, isHighlighter)
        )
    )

    /**
     * Serialize a PdfAnnotation.TextNote to a JSON string.
     */
    fun serializeTextAnnotation(
        annotationId: String,
        pageIndex: Int,
        text: String,
        positionX: Float,
        positionY: Float,
        color: Long,
        fontSize: Float
    ): String = gson.toJson(
        AnnotationSnapshot(
            type = TYPE_ADD_TEXT,
            annotationId = annotationId,
            pageIndex = pageIndex,
            textData = AddTextPayload(annotationId, text, positionX, positionY, color, fontSize)
        )
    )

    /**
     * Deserialize a snapshot JSON back to determine the annotation type and data.
     */
    fun deserializeSnapshot(json: String): AnnotationSnapshot {
        return gson.fromJson(json, AnnotationSnapshot::class.java)
    }

    // ─── Internal Payload DTOs ──────────────────────────────────

    data class AddPathPayload(
        val annotationId: String,
        val points: List<SerializableOffset>,
        val color: Long,
        val strokeWidth: Float,
        val isHighlighter: Boolean
    )

    data class AddTextPayload(
        val annotationId: String,
        val text: String,
        val positionX: Float,
        val positionY: Float,
        val color: Long,
        val fontSize: Float
    )

    data class RemovePayload(
        val annotationId: String,
        val removedAnnotationPayload: String
    )

    data class UpdatePayload(
        val annotationId: String,
        val previousPayload: String,
        val newPayload: String
    )

    /**
     * A full snapshot of an annotation for storing inside remove/update commands.
     */
    data class AnnotationSnapshot(
        val type: String,
        val annotationId: String,
        val pageIndex: Int,
        val pathData: AddPathPayload? = null,
        val textData: AddTextPayload? = null
    )
}
