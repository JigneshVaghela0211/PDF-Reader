package com.pdf.pdfreader.domain.usecase

import android.util.Log
import com.pdf.pdfreader.domain.model.AnnotationCommand
import com.pdf.pdfreader.domain.repository.UndoRedoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe Undo/Redo Manager using Command Pattern.
 *
 * All stack operations are protected by a [Mutex] to prevent race conditions.
 * DB operations are dispatched to [Dispatchers.IO].
 *
 * Architecture:
 * - In-memory stacks (ArrayDeque) for instant UI response
 * - Room DB for persistence across app kills
 * - Mutex for thread safety under concurrent access
 * - Retry mechanism for DB write failures
 */
@Singleton
class UndoRedoManager @Inject constructor(
    private val repository: UndoRedoRepository
) {
    companion object {
        private const val TAG = "UndoRedoManager"
        const val MAX_STACK_SIZE = 50
        private const val MAX_DB_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }

    private val mutex = Mutex()

    // In-memory stacks — the primary source for immediate operations
    private val undoStack = ArrayDeque<AnnotationCommand>()
    private val redoStack = ArrayDeque<AnnotationCommand>()

    // State exposed to UI via StateFlow — no recomposition loops
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _lastAction = MutableStateFlow<UndoRedoAction?>(null)
    val lastAction: StateFlow<UndoRedoAction?> = _lastAction.asStateFlow()

    /**
     * Execute a new command.
     *
     * 1. Push to undo stack (memory — instant)
     * 2. Clear redo stack (new action invalidates redo history)
     * 3. Persist to DB (async, with retry on failure)
     * 4. Enforce stack size limit
     */
    suspend fun execute(command: AnnotationCommand) {
        mutex.withLock {
            Log.d(TAG, "Execute: ${command.id} type=${command::class.simpleName}")

            // Push to undo stack
            undoStack.addLast(command)

            // Clear redo stack — a new action invalidates all redos
            redoStack.clear()

            // Enforce stack size limit
            val pruned = mutableListOf<AnnotationCommand>()
            while (undoStack.size > MAX_STACK_SIZE) {
                pruned.add(undoStack.removeFirst())
            }

            updateStateFlows()

            // Persist to DB (async, outside lock would cause ordering issues, but
            // we keep it inside lock for consistency — DB ops are fast for single inserts)
            try {
                persistWithRetry {
                    // Clear redo in DB
                    repository.clearRedoStack(command.pdfPath)

                    // Save the new command
                    repository.saveCommand(command)

                    // Delete pruned commands from DB
                    pruned.forEach { repository.deleteCommand(it.id) }
                }
            } catch (e: Exception) {
                // Memory state is still valid even if DB fails
                Log.e(TAG, "DB persist failed for execute, memory state intact", e)
            }
        }
    }

    /**
     * Undo the last command.
     *
     * 1. Pop from undo stack
     * 2. Push to redo stack
     * 3. Mark as undone in DB
     *
     * @return The undone command, or null if nothing to undo.
     */
    suspend fun undo(): AnnotationCommand? {
        return mutex.withLock {
            if (undoStack.isEmpty()) {
                Log.d(TAG, "Undo: nothing to undo")
                return@withLock null
            }

            val command = undoStack.removeLast()
            redoStack.addLast(command)
            updateStateFlows()
            _lastAction.value = UndoRedoAction.Undo(command)

            Log.d(TAG, "Undo: ${command.id} type=${command::class.simpleName}")

            try {
                persistWithRetry {
                    repository.markAsUndone(command.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB persist failed for undo, memory state intact", e)
            }

            command
        }
    }

    /**
     * Redo the last undone command.
     *
     * 1. Pop from redo stack
     * 2. Push to undo stack
     * 3. Mark as active in DB
     *
     * @return The redone command, or null if nothing to redo.
     */
    suspend fun redo(): AnnotationCommand? {
        return mutex.withLock {
            if (redoStack.isEmpty()) {
                Log.d(TAG, "Redo: nothing to redo")
                return@withLock null
            }

            val command = redoStack.removeLast()
            undoStack.addLast(command)
            updateStateFlows()
            _lastAction.value = UndoRedoAction.Redo(command)

            Log.d(TAG, "Redo: ${command.id} type=${command::class.simpleName}")

            try {
                persistWithRetry {
                    repository.markAsActive(command.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB persist failed for redo, memory state intact", e)
            }

            command
        }
    }

    /**
     * Restore state from DB on app restart.
     *
     * Loads all commands for a PDF, partitions them into:
     * - active (isUndone=false) → undo stack
     * - undone (isUndone=true) → redo stack
     *
     * @return List of active commands to rebuild the annotation state.
     */
    suspend fun restoreState(pdfPath: String): List<AnnotationCommand> {
        return mutex.withLock {
            Log.d(TAG, "Restoring state for: $pdfPath")

            undoStack.clear()
            redoStack.clear()

            try {
                val allCommands = withContext(Dispatchers.IO) {
                    repository.loadCommandsForPdf(pdfPath)
                }

                // We need to know which are undone — load entities directly
                val entities = withContext(Dispatchers.IO) {
                    // Load raw entities to check isUndone flag
                    repository.loadCommandsForPdf(pdfPath)
                }

                // Since our repository returns domain objects, we need to reload
                // with the isUndone info. Let's use the DAO through repository pattern.
                // For now, we load all commands and check the DB state.
                val commandDao = withContext(Dispatchers.IO) {
                    (repository as? com.pdf.pdfreader.data.repository.UndoRedoRepositoryImpl)
                }

                // Simpler approach: load all entities from DAO directly via repository extension
                val activeCommands = mutableListOf<AnnotationCommand>()
                val undoneCommands = mutableListOf<AnnotationCommand>()

                // Use the repository to load and separate
                val dbEntities = withContext(Dispatchers.IO) {
                    try {
                        val repoImpl = repository as com.pdf.pdfreader.data.repository.UndoRedoRepositoryImpl
                        repoImpl.loadCommandEntities(pdfPath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load entities, falling back", e)
                        null
                    }
                }

                if (dbEntities != null) {
                    for (entity in dbEntities) {
                        val command = com.pdf.pdfreader.data.local.CommandSerializer.fromEntity(entity)
                        if (entity.isUndone) {
                            undoneCommands.add(command)
                        } else {
                            activeCommands.add(command)
                        }
                    }
                }

                // Rebuild stacks
                activeCommands.forEach { undoStack.addLast(it) }
                undoneCommands.forEach { redoStack.addLast(it) }

                updateStateFlows()

                Log.d(TAG, "Restored: ${undoStack.size} undo, ${redoStack.size} redo")
                activeCommands
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore state", e)
                updateStateFlows()
                emptyList()
            }
        }
    }

    /**
     * Clear all undo/redo state for a PDF.
     * Used after saving annotations to PDF (commands are now baked in).
     */
    suspend fun clearAll(pdfPath: String) {
        mutex.withLock {
            Log.d(TAG, "Clearing all state for: $pdfPath")
            undoStack.clear()
            redoStack.clear()
            updateStateFlows()
            _lastAction.value = null

            try {
                withContext(Dispatchers.IO) {
                    repository.clearAllCommands(pdfPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DB commands", e)
            }
        }
    }

    /**
     * Get the current list of active commands (for rebuilding annotation state).
     */
    suspend fun getActiveCommands(): List<AnnotationCommand> {
        return mutex.withLock {
            undoStack.toList()
        }
    }

    // ─── Internal Helpers ───────────────────────────────────────

    private fun updateStateFlows() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /**
     * Retry mechanism for DB operations.
     * If a write fails, retry up to [MAX_DB_RETRIES] times with delay.
     * Memory state remains intact regardless of DB outcome.
     */
    private suspend fun persistWithRetry(operation: suspend () -> Unit) {
        var attempt = 0
        while (attempt < MAX_DB_RETRIES) {
            try {
                withContext(Dispatchers.IO) {
                    operation()
                }
                return // Success
            } catch (e: Exception) {
                attempt++
                Log.w(TAG, "DB operation failed (attempt $attempt/$MAX_DB_RETRIES)", e)
                if (attempt < MAX_DB_RETRIES) {
                    delay(RETRY_DELAY_MS)
                } else {
                    throw e // Give up after max retries
                }
            }
        }
    }
}

/**
 * Represents the last undo/redo action for UI feedback.
 */
sealed class UndoRedoAction {
    data class Undo(val command: AnnotationCommand) : UndoRedoAction()
    data class Redo(val command: AnnotationCommand) : UndoRedoAction()
}
