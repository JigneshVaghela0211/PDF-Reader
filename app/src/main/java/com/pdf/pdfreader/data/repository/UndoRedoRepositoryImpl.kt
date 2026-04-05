package com.pdf.pdfreader.data.repository

import android.util.Log
import com.pdf.pdfreader.data.local.AnnotationCommandDao
import com.pdf.pdfreader.data.local.CommandSerializer
import com.pdf.pdfreader.domain.model.AnnotationCommand
import com.pdf.pdfreader.domain.repository.UndoRedoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [UndoRedoRepository] backed by Room DB.
 *
 * All DB operations run on [Dispatchers.IO] for thread safety.
 * This is the single source of truth for command persistence.
 */
@Singleton
class UndoRedoRepositoryImpl @Inject constructor(
    private val commandDao: AnnotationCommandDao
) : UndoRedoRepository {

    companion object {
        private const val TAG = "UndoRedoRepo"
    }

    override suspend fun saveCommand(command: AnnotationCommand) {
        withContext(Dispatchers.IO) {
            try {
                val entity = CommandSerializer.toEntity(command)
                commandDao.insertCommand(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save command: ${command.id}", e)
                throw e
            }
        }
    }

    override suspend fun loadCommandsForPdf(pdfPath: String): List<AnnotationCommand> {
        return withContext(Dispatchers.IO) {
            try {
                commandDao.getCommandsForPdf(pdfPath).map { entity ->
                    CommandSerializer.fromEntity(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load commands for: $pdfPath", e)
                emptyList()
            }
        }
    }

    override suspend fun markAsUndone(commandId: String) {
        withContext(Dispatchers.IO) {
            try {
                commandDao.updateUndoneState(commandId, isUndone = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as undone: $commandId", e)
                throw e
            }
        }
    }

    override suspend fun markAsActive(commandId: String) {
        withContext(Dispatchers.IO) {
            try {
                commandDao.updateUndoneState(commandId, isUndone = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as active: $commandId", e)
                throw e
            }
        }
    }

    override suspend fun clearRedoStack(pdfPath: String) {
        withContext(Dispatchers.IO) {
            try {
                commandDao.deleteRedoCommands(pdfPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear redo stack: $pdfPath", e)
            }
        }
    }

    override suspend fun deleteCommand(commandId: String) {
        withContext(Dispatchers.IO) {
            try {
                commandDao.deleteCommand(commandId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete command: $commandId", e)
            }
        }
    }

    override suspend fun deleteOldestCommands(pdfPath: String, keepCount: Int) {
        withContext(Dispatchers.IO) {
            try {
                val activeCount = commandDao.getActiveCommandCount(pdfPath)
                val toDelete = activeCount - keepCount
                if (toDelete > 0) {
                    repeat(toDelete) {
                        val oldest = commandDao.getOldestActiveCommand(pdfPath)
                        if (oldest != null) {
                            commandDao.deleteCommand(oldest.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete oldest commands: $pdfPath", e)
            }
        }
    }

    override suspend fun clearAllCommands(pdfPath: String) {
        withContext(Dispatchers.IO) {
            try {
                commandDao.deleteAllCommandsForPdf(pdfPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all commands: $pdfPath", e)
            }
        }
    }

    override suspend fun getActiveCommandCount(pdfPath: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                commandDao.getActiveCommandCount(pdfPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get active count: $pdfPath", e)
                0
            }
        }
    }

    /**
     * Load raw command entities with isUndone flag for state restoration.
     * This is needed by [com.pdf.pdfreader.domain.usecase.UndoRedoManager.restoreState]
     * to correctly partition commands into undo/redo stacks.
     */
    suspend fun loadCommandEntities(pdfPath: String): List<com.pdf.pdfreader.data.local.AnnotationCommandEntity> {
        return withContext(Dispatchers.IO) {
            try {
                commandDao.getCommandsForPdf(pdfPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load command entities: $pdfPath", e)
                emptyList()
            }
        }
    }
}
