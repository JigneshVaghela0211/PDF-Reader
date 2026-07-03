package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room SCHEMA container for document editing sessions.
 *
 * Defined so the [DocumentSessionEntity] table and [DocumentSessionDao] form a complete, valid Room
 * schema. This chunk provides **schema only**: the database is intentionally NOT provided through
 * Hilt and is never instantiated, so no persistence occurs. Wiring it (DI provider, migrations) is
 * deliberately deferred to when session persistence / undo-redo is implemented.
 *
 * Kept separate from the app's main `AppDatabase` so the session schema can evolve without touching
 * the shared database or its migrations.
 */
@Database(
    entities = [DocumentSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DocumentSessionDatabase : RoomDatabase() {
    abstract fun documentSessionDao(): DocumentSessionDao

    companion object {
        const val DATABASE_NAME = "document_session_db"
    }
}
