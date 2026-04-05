package com.pdf.pdfreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for annotation undo/redo commands.
 *
 * All operations are suspend functions for coroutine-based access.
 */
@Dao
interface AnnotationCommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: AnnotationCommandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<AnnotationCommandEntity>)

    @Query("SELECT * FROM annotation_commands WHERE pdfPath = :pdfPath ORDER BY createdAt ASC")
    suspend fun getCommandsForPdf(pdfPath: String): List<AnnotationCommandEntity>

    @Query("UPDATE annotation_commands SET isUndone = :isUndone WHERE id = :id")
    suspend fun updateUndoneState(id: String, isUndone: Boolean)

    @Query("DELETE FROM annotation_commands WHERE pdfPath = :pdfPath AND isUndone = 1")
    suspend fun deleteRedoCommands(pdfPath: String)

    @Query("DELETE FROM annotation_commands WHERE id = :id")
    suspend fun deleteCommand(id: String)

    @Query("DELETE FROM annotation_commands WHERE pdfPath = :pdfPath")
    suspend fun deleteAllCommandsForPdf(pdfPath: String)

    @Query("SELECT COUNT(*) FROM annotation_commands WHERE pdfPath = :pdfPath AND isUndone = 0")
    suspend fun getActiveCommandCount(pdfPath: String): Int

    /**
     * Get the oldest active (non-undone) command for a PDF, used for stack pruning.
     */
    @Query(
        "SELECT * FROM annotation_commands WHERE pdfPath = :pdfPath AND isUndone = 0 " +
                "ORDER BY createdAt ASC LIMIT 1"
    )
    suspend fun getOldestActiveCommand(pdfPath: String): AnnotationCommandEntity?
}
