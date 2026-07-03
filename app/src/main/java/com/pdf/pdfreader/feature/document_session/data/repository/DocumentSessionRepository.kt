package com.pdf.pdfreader.feature.document_session.data.repository

import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the document editing sessions that are currently open.
 *
 * Foundation only: this is an **in-memory** store — no Room persistence is wired in this chunk (the
 * [com.pdf.pdfreader.feature.document_session.data.database] schema exists but is unused). It just
 * keeps live sessions so the lifecycle (open → create, close → remove) is observable.
 */
@Singleton
class DocumentSessionRepository @Inject constructor() {

    private val _sessions = MutableStateFlow<Map<String, DocumentSession>>(emptyMap())
    /** All currently-open sessions, keyed by sessionId. */
    val sessions: StateFlow<Map<String, DocumentSession>> = _sessions.asStateFlow()

    /** Store (or replace) a session. */
    fun put(session: DocumentSession) {
        _sessions.value = _sessions.value + (session.sessionId to session)
    }

    fun get(sessionId: String): DocumentSession? = _sessions.value[sessionId]

    fun getByPath(documentPath: String): DocumentSession? =
        _sessions.value.values.firstOrNull { it.documentPath == documentPath }

    /** Remove a session from the store. */
    fun remove(sessionId: String) {
        _sessions.value = _sessions.value - sessionId
    }
}
