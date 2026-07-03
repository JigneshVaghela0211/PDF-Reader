package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room SCHEMA (DAO) for [DocumentSessionEntity].
 *
 * Declared as part of the schema foundation. Nothing calls these methods in this chunk — there is no
 * persistence logic yet; they exist so the schema is complete and future work can persist sessions
 * without changing the shape.
 */
@Dao
interface DocumentSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DocumentSessionEntity)

    @Query("SELECT * FROM document_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): DocumentSessionEntity?

    @Query("SELECT * FROM document_sessions WHERE documentPath = :documentPath")
    suspend fun getByPath(documentPath: String): DocumentSessionEntity?

    @Query("SELECT * FROM document_sessions")
    suspend fun getAll(): List<DocumentSessionEntity>

    @Delete
    suspend fun delete(session: DocumentSessionEntity)

    @Query("DELETE FROM document_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)
}
