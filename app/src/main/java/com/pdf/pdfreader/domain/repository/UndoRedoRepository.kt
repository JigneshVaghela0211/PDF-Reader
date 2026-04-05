package com.pdf.pdfreader.domain.repository

import com.pdf.pdfreader.domain.model.AnnotationCommand

/**
 * Repository interface for undo/redo command persistence.
 *
 * Acts as the single source of truth boundary between the domain layer
 * and the Room DB storage.
 */
interface UndoRedoRepository {

    /**
     * Save a command to the database.
     */
    suspend fun saveCommand(command: AnnotationCommand)

    /**
     * Load all commands for a given PDF, ordered by creation time.
     */
    suspend fun loadCommandsForPdf(pdfPath: String): List<AnnotationCommand>

    /**
     * Mark a command as undone (moves it to redo stack conceptually).
     */
    suspend fun markAsUndone(commandId: String)

    /**
     * Mark a command as active (moves it back to undo stack conceptually).
     */
    suspend fun markAsActive(commandId: String)

    /**
     * Delete all undone commands for a PDF (clear redo stack in DB).
     */
    suspend fun clearRedoStack(pdfPath: String)

    /**
     * Delete a specific command from the database.
     */
    suspend fun deleteCommand(commandId: String)

    /**
     * Delete the oldest active commands, keeping only [keepCount] active commands.
     */
    suspend fun deleteOldestCommands(pdfPath: String, keepCount: Int)

    /**
     * Delete all commands for a PDF (e.g., after saving annotations to PDF).
     */
    suspend fun clearAllCommands(pdfPath: String)

    /**
     * Get the count of active (non-undone) commands for a PDF.
     */
    suspend fun getActiveCommandCount(pdfPath: String): Int
}
