package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SCHEMA for a document editing session.
 *
 * This chunk defines the schema only — the table is not written to or read from anywhere yet (no
 * persistence logic, no undo/redo). [saveState] is stored as the enum name (String) so no
 * TypeConverter is required.
 */
@Entity(tableName = "document_sessions")
data class DocumentSessionEntity(
    @PrimaryKey val sessionId: String,
    val documentPath: String,
    val openedTime: Long,
    val modifiedTime: Long,
    val editCount: Int,
    val saveState: String
)
