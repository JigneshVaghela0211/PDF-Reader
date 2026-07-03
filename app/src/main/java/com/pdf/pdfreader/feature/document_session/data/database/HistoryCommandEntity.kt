package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one command in a session's **chronological history timeline** (MC11).
 *
 * There are no separate undo/redo tables — every recorded command lives here once, ordered by
 * [orderIndex] (0 = oldest). Whether a command is "undone" is derived from the session's
 * `currentPointer` (see [DocumentSessionEntity]), not stored per-row. [commandVersion] stamps the
 * command's serialized shape for forward migration.
 */
@Entity(
    tableName = "history_commands",
    indices = [Index(value = ["sessionId"])]
)
data class HistoryCommandEntity(
    @PrimaryKey val commandId: String,
    val sessionId: String,
    val commandType: String,
    val pageIndex: Int,
    val description: String,
    val timestamp: Long,
    /** Position in the chronological timeline (0 = oldest). */
    val orderIndex: Int,
    val commandVersion: Int
) {
    companion object {
        /** Bump when the persisted command shape changes (future migrations). */
        const val CURRENT_COMMAND_VERSION = 1
    }
}
