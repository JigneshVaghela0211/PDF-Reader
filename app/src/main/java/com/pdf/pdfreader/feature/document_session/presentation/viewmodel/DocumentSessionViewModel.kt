package com.pdf.pdfreader.feature.document_session.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.feature.document_session.data.repository.DocumentSessionRepository
import com.pdf.pdfreader.feature.document_session.data.repository.HistoryPersistenceRepository
import com.pdf.pdfreader.feature.document_session.domain.command.PlaceholderCommand
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentIdentity
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import com.pdf.pdfreader.feature.document_session.domain.model.EditOperationType
import com.pdf.pdfreader.feature.document_session.domain.model.SaveState
import com.pdf.pdfreader.feature.document_session.domain.usecase.CloseDocumentSessionUseCase
import com.pdf.pdfreader.feature.document_session.domain.usecase.CreateDocumentSessionUseCase
import com.pdf.pdfreader.feature.document_session.domain.usecase.SessionUndoRedoEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val undoRedoEngine: SessionUndoRedoEngine,
    private val persistence: HistoryPersistenceRepository
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

    /**
     * Open PDF → restore the persisted session + undo/redo stacks for this file if one exists
     * (MC11), otherwise create a fresh session and persist it. Keyed on the document identity so
     * reopening the same file after an app restart brings its history back.
     */
    fun openDocument(documentPath: String) {
        viewModelScope.launch {
            val identity = DocumentIdentity.fromPath(documentPath)
            val restored = withContext(Dispatchers.IO) { persistence.load(identity.documentId) }

            val session = if (restored != null) {
                undoRedoEngine.restore(restored.session.sessionId, restored.timeline, restored.pointer)
                repository.put(restored.session)
                val redoable = restored.timeline.size - restored.pointer
                Log.d(TAG, "Session restored: id=${restored.session.sessionId} timeline=${restored.timeline.size} pointer=${restored.pointer} (undo=${restored.pointer} redo=$redoable)")
                restored.session
            } else {
                val created = createDocumentSession(documentPath)
                withContext(Dispatchers.IO) { persistence.persist(created, emptyList(), 0) }
                Log.d(TAG, "Session created: id=${created.sessionId} path=$documentPath state=${created.saveState}")
                created
            }

            _activeSession.value = session
            syncUndoRedoFlags(session.sessionId)
        }
    }

    /**
     * Close PDF → drop the in-memory session/history but KEEP it persisted in Room, so reopening the
     * document (even after an app restart) restores it. A final persist guards against any change not
     * yet flushed.
     */
    fun closeDocument() {
        val session = _activeSession.value ?: return
        val timeline = undoRedoEngine.timeline(session.sessionId)
        val pointer = undoRedoEngine.pointer(session.sessionId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { persistence.persist(session, timeline, pointer) }
        }
        undoRedoEngine.clear(session.sessionId)
        closeDocumentSession(session.sessionId)
        _activeSession.value = null
        _canUndo.value = false
        _canRedo.value = false
        Log.d(TAG, "Session closed (kept in Room): id=${session.sessionId}")
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

    /** Mark the active session's edits as persisted. Foundation only (no actual document save here). */
    fun markSaved() {
        val current = _activeSession.value ?: return
        val updated = current.copy(saveState = SaveState.SAVED)
        repository.put(updated)
        _activeSession.value = updated
        persistCurrent(updated)
    }

    /** Re-derive session edit-count / modified-time / save-state from the undo history, then persist. */
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
        persistCurrent(updated)
    }

    /** Persist the session and its current history timeline + pointer to Room (MC11). */
    private fun persistCurrent(session: DocumentSession) {
        val timeline = undoRedoEngine.timeline(session.sessionId)
        val pointer = undoRedoEngine.pointer(session.sessionId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { persistence.persist(session, timeline, pointer) }
        }
    }

    private fun syncUndoRedoFlags(sessionId: String) {
        _canUndo.value = undoRedoEngine.canUndo(sessionId)
        _canRedo.value = undoRedoEngine.canRedo(sessionId)
    }
}
