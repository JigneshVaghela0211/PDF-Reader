package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for [DocumentSessionEntity] (MC11 — persisted).
 */
@Dao
interface DocumentSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DocumentSessionEntity)

    @Query("SELECT * FROM document_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): DocumentSessionEntity?

    /** Restore lookup: the most recent session for a document identity. */
    @Query("SELECT * FROM document_sessions WHERE documentId = :documentId ORDER BY modifiedTime DESC LIMIT 1")
    suspend fun getByDocumentId(documentId: String): DocumentSessionEntity?

    @Query("SELECT * FROM document_sessions WHERE documentPath = :documentPath")
    suspend fun getByPath(documentPath: String): DocumentSessionEntity?

    @Query("SELECT * FROM document_sessions")
    suspend fun getAll(): List<DocumentSessionEntity>

    @Delete
    suspend fun delete(session: DocumentSessionEntity)

    @Query("DELETE FROM document_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)
}
