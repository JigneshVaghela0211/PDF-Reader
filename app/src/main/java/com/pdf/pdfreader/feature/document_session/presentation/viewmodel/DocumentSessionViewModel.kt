package com.pdf.pdfreader.feature.document_session.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.pdf.pdfreader.feature.document_session.data.repository.DocumentSessionRepository
import com.pdf.pdfreader.feature.document_session.domain.command.PlaceholderCommand
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import com.pdf.pdfreader.feature.document_session.domain.model.EditOperationType
import com.pdf.pdfreader.feature.document_session.domain.model.SaveState
import com.pdf.pdfreader.feature.document_session.domain.usecase.CloseDocumentSessionUseCase
import com.pdf.pdfreader.feature.document_session.domain.usecase.CreateDocumentSessionUseCase
import com.pdf.pdfreader.feature.document_session.domain.usecase.SessionUndoRedoEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns the editing-session lifecycle for the open PDF: **open → create session**, **close → close
 * session**. It only delegates to the use-cases and exposes the active session; there is no undo/redo,
 * no persistence and no save in this chunk.
 */
@HiltViewModel
class DocumentSessionViewModel @Inject constructor(
    private val createDocumentSession: CreateDocumentSessionUseCase,
    private val closeDocumentSession: CloseDocumentSessionUseCase,
    private val repository: DocumentSessionRepository,
    private val undoRedoEngine: SessionUndoRedoEngine
) : ViewModel() {

    companion object {
        private const val TAG = "DocSessionVM"
    }

    private val _activeSession = MutableStateFlow<DocumentSession?>(null)
    val activeSession: StateFlow<DocumentSession?> = _activeSession.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Open PDF → create (or reuse) the session for [documentPath]. */
    fun openDocument(documentPath: String) {
        val session = createDocumentSession(documentPath)
        _activeSession.value = session
        syncUndoRedoFlags(session.sessionId)
        Log.d(TAG, "Session created: id=${session.sessionId} path=$documentPath state=${session.saveState}")
    }

    /** Close PDF → close the active session and drop its in-memory history. */
    fun closeDocument() {
        val session = _activeSession.value ?: return
        undoRedoEngine.clear(session.sessionId)
        val closed = closeDocumentSession(session.sessionId)
        _activeSession.value = null
        _canUndo.value = false
        _canRedo.value = false
        Log.d(TAG, "Session closed: id=${session.sessionId} (wasOpen=$closed)")
    }

    /**
     * Record an edit against the current session: builds a [PlaceholderCommand], pushes it onto the
     * session's undo history (invalidating redo), and refreshes tracking. In-memory only — no
     * persistence, no save.
     */
    fun recordEdit(type: EditOperationType, pageIndex: Int, description: String = "") {
        val session = _activeSession.value ?: return
        val command = PlaceholderCommand(
            commandType = type,
            pageIndex = pageIndex,
            description = description
        )
        undoRedoEngine.record(session.sessionId, command)
        Log.d(TAG, "recordEdit: ${command.commandType} page=${command.pageIndex} '${command.description}' -> applied=${undoRedoEngine.appliedCount(session.sessionId)}")
        syncSessionFromHistory(session)
    }

    /** Undo the newest edit of the current session. Operates only on the active session. */
    fun undo() {
        val session = _activeSession.value ?: return
        val command = undoRedoEngine.undo(session.sessionId) ?: return
        Log.d(TAG, "undo: ${command.commandType} (${command.commandId}) -> applied=${undoRedoEngine.appliedCount(session.sessionId)}")
        syncSessionFromHistory(session)
    }

    /** Redo the most-recently-undone edit of the current session. */
    fun redo() {
        val session = _activeSession.value ?: return
        val command = undoRedoEngine.redo(session.sessionId) ?: return
        Log.d(TAG, "redo: ${command.commandType} (${command.commandId}) -> applied=${undoRedoEngine.appliedCount(session.sessionId)}")
        syncSessionFromHistory(session)
    }

    /** Mark the active session's edits as persisted. Foundation only (no actual save here). */
    fun markSaved() {
        val current = _activeSession.value ?: return
        val updated = current.copy(saveState = SaveState.SAVED)
        repository.put(updated)
        _activeSession.value = updated
    }

    /** Re-derive session edit-count / modified-time / save-state from the undo history. */
    private fun syncSessionFromHistory(session: DocumentSession) {
        val applied = undoRedoEngine.appliedCount(session.sessionId)
        val updated = session.copy(
            editCount = applied,
            modifiedTime = System.currentTimeMillis(),
            saveState = if (applied == 0) SaveState.CLEAN else SaveState.MODIFIED
        )
        repository.put(updated)
        _activeSession.value = updated
        syncUndoRedoFlags(session.sessionId)
    }

    private fun syncUndoRedoFlags(sessionId: String) {
        _canUndo.value = undoRedoEngine.canUndo(sessionId)
        _canRedo.value = undoRedoEngine.canRedo(sessionId)
    }
}
