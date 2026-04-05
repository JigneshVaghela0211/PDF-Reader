package com.pdf.pdfreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting undo/redo annotation commands.
 *
 * Stores only lightweight data — coordinates, colors, text, stroke widths.
 * NO bitmaps or large objects are ever stored.
 */
@Entity(tableName = "annotation_commands")
data class AnnotationCommandEntity(
    @PrimaryKey val id: String,
    val pdfPath: String,
    val pageIndex: Int,
    val type: String,       // "ADD_PATH", "ADD_TEXT", "REMOVE", "UPDATE"
    val payload: String,    // JSON — lightweight data only
    val isUndone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
