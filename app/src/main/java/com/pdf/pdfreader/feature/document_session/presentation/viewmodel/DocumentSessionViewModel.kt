package com.pdf.pdfreader.feature.document_session.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.pdf.pdfreader.feature.document_session.data.repository.DocumentSessionRepository
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import com.pdf.pdfreader.feature.document_session.domain.model.SaveState
import com.pdf.pdfreader.feature.document_session.domain.usecase.CloseDocumentSessionUseCase
import com.pdf.pdfreader.feature.document_session.domain.usecase.CreateDocumentSessionUseCase
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
    private val repository: DocumentSessionRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DocSessionVM"
    }

    private val _activeSession = MutableStateFlow<DocumentSession?>(null)
    val activeSession: StateFlow<DocumentSession?> = _activeSession.asStateFlow()

    /** Open PDF → create (or reuse) the session for [documentPath]. */
    fun openDocument(documentPath: String) {
        val session = createDocumentSession(documentPath)
        _activeSession.value = session
        Log.d(TAG, "Session created: id=${session.sessionId} path=$documentPath state=${session.saveState}")
    }

    /** Close PDF → close the active session. */
    fun closeDocument() {
        val session = _activeSession.value ?: return
        val closed = closeDocumentSession(session.sessionId)
        _activeSession.value = null
        Log.d(TAG, "Session closed: id=${session.sessionId} (wasOpen=$closed)")
    }

    /**
     * Record that an edit happened: bump [DocumentSession.editCount] / modified time and mark the
     * session [SaveState.MODIFIED]. Foundation for tracking only — it does NOT store the operation,
     * and it is not yet wired to actual edits.
     */
    fun recordEdit() {
        val current = _activeSession.value ?: return
        val updated = current.copy(
            editCount = current.editCount + 1,
            modifiedTime = System.currentTimeMillis(),
            saveState = SaveState.MODIFIED
        )
        repository.put(updated)
        _activeSession.value = updated
    }

    /** Mark the active session's edits as persisted. Foundation only (no actual save here). */
    fun markSaved() {
        val current = _activeSession.value ?: return
        val updated = current.copy(saveState = SaveState.SAVED)
        repository.put(updated)
        _activeSession.value = updated
    }
}
