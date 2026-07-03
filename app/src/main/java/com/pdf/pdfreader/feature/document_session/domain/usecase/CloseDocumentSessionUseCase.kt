package com.pdf.pdfreader.feature.document_session.domain.usecase

import com.pdf.pdfreader.feature.document_session.data.repository.DocumentSessionRepository
import javax.inject.Inject

/**
 * Closes an editing session: removes it from the repository.
 *
 * Foundation only — no flush/save is performed (there is no persistence yet). Returns true if a
 * session with [sessionId] was open and is now closed.
 */
class CloseDocumentSessionUseCase @Inject constructor(
    private val repository: DocumentSessionRepository
) {
    operator fun invoke(sessionId: String): Boolean {
        if (repository.get(sessionId) == null) return false
        repository.remove(sessionId)
        return true
    }
}
