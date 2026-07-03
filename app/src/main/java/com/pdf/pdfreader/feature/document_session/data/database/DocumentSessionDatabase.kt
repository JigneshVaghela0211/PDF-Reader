package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for document editing sessions and their undo/redo history (MC11 — now persisted).
 *
 * Kept **separate** from the app's main `AppDatabase` so the session/history schema can evolve
 * independently and never risks the shared database's migrations. It only stores editing-session
 * state (session + undo/redo stacks) — no user documents, no save/export data.
 */
@Database(
    entities = [DocumentSessionEntity::class, HistoryCommandEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DocumentSessionDatabase : RoomDatabase() {
    abstract fun documentSessionDao(): DocumentSessionDao
    abstract fun historyCommandDao(): HistoryCommandDao

    companion object {
        const val DATABASE_NAME = "document_session_db"
    }
}
