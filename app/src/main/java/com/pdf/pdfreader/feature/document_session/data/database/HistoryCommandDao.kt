package com.pdf.pdfreader.feature.document_session.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for [HistoryCommandEntity] (MC11).
 *
 * The persistence layer rewrites a session's history on each change: [deleteBySession] then
 * [upsertAll]. [getBySession] returns rows for restore (caller splits UNDO/REDO and sorts by
 * orderIndex).
 */
@Dao
interface HistoryCommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(commands: List<HistoryCommandEntity>)

    @Query("SELECT * FROM history_commands WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<HistoryCommandEntity>

    @Query("DELETE FROM history_commands WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
