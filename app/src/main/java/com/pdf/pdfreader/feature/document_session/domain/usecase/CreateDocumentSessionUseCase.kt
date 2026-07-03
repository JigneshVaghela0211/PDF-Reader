package com.pdf.pdfreader.feature.document_session.domain.usecase

import com.pdf.pdfreader.feature.document_session.data.repository.DocumentSessionRepository
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentIdentity
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import com.pdf.pdfreader.feature.document_session.domain.model.SaveState
import java.util.UUID
import javax.inject.Inject

/**
 * Opens a new editing session for [documentPath]: generates a unique sessionId, stamps the open
 * time, and registers it with the repository. If a session for the same path is already open it is
 * reused (opening the same document twice should not spawn duplicates).
 *
 * Foundation only — creates and tracks the session; no persistence, no undo/redo.
 */
class CreateDocumentSessionUseCase @Inject constructor(
    private val repository: DocumentSessionRepository
) {
    operator fun invoke(documentPath: String): DocumentSession {
        repository.getByPath(documentPath)?.let { return it }

        val now = System.currentTimeMillis()
        val session = DocumentSession(
            sessionId = UUID.randomUUID().toString(),
            identity = DocumentIdentity.fromPath(documentPath),
            openedTime = now,
            modifiedTime = now,
            editCount = 0,
            saveState = SaveState.CLEAN
        )
        repository.put(session)
        return session
    }
}
