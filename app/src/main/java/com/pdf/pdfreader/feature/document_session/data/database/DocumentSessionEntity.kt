package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a document editing session (MC11 — now actually persisted).
 *
 * Keyed by [sessionId], but looked up by [documentId] on reopen (a fresh open reuses the persisted
 * session for the same file). [saveState] is stored as the enum name. Identity fields are flattened
 * in so a session restores without a separate identity table.
 *
 * [currentPointer] is the head of the session's chronological history timeline (how many commands
 * are applied); undo/redo are derived from it. [historyVersion] stamps the timeline's persisted
 * shape for forward migration.
 */
@Entity(
    tableName = "document_sessions",
    indices = [Index(value = ["documentId"])]
)
data class DocumentSessionEntity(
    @PrimaryKey val sessionId: String,
    val documentId: String,
    val documentPath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val sha256: String?,
    val openedTime: Long,
    val modifiedTime: Long,
    val editCount: Int,
    val saveState: String,
    val schemaVersion: Int,
    val sessionVersion: Int,
    val currentPointer: Int,
    val historyVersion: Int
) {
    companion object {
        /** Bump when the persisted history-timeline shape changes (future migrations). */
        const val CURRENT_HISTORY_VERSION = 1
    }
}
