package com.pdf.pdfreader.feature.document_session.data.repository

import com.pdf.pdfreader.feature.document_session.data.database.DocumentSessionDao
import com.pdf.pdfreader.feature.document_session.data.database.DocumentSessionEntity
import com.pdf.pdfreader.feature.document_session.data.database.HistoryCommandDao
import com.pdf.pdfreader.feature.document_session.data.database.HistoryCommandEntity
import com.pdf.pdfreader.feature.document_session.domain.command.HistoryCommand
import com.pdf.pdfreader.feature.document_session.domain.command.PlaceholderCommand
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentIdentity
import com.pdf.pdfreader.feature.document_session.domain.model.DocumentSession
import com.pdf.pdfreader.feature.document_session.domain.model.EditOperationType
import com.pdf.pdfreader.feature.document_session.domain.model.SaveState
import javax.inject.Inject
import javax.inject.Singleton

/** A session plus its chronological history timeline and pointer, reconstructed from Room. */
data class RestoredSession(
    val session: DocumentSession,
    val timeline: List<HistoryCommand>,
    val pointer: Int
)

/**
 * Room-backed persistence of editing sessions and their **chronological history timeline** (MC11).
 *
 * One command table ordered by `orderIndex`, plus a `currentPointer` on the session row — no
 * separate undo/redo tables. Persistence is keyed on [DocumentIdentity.documentId] for restore. Each
 * change rewrites the timeline rows in full (simple and correct for small histories). Scope:
 * persistence only — no save, export or recovery UI.
 */
@Singleton
class HistoryPersistenceRepository @Inject constructor(
    private val sessionDao: DocumentSessionDao,
    private val historyDao: HistoryCommandDao
) {

    /** Load the persisted session + timeline + pointer for a document, or null if none exists. */
    suspend fun load(documentId: String): RestoredSession? {
        val entity = sessionDao.getByDocumentId(documentId) ?: return null
        val timeline = historyDao.getBySession(entity.sessionId)
            .sortedBy { it.orderIndex }
            .map { it.toCommand() }
        return RestoredSession(
            session = entity.toDomain(),
            timeline = timeline,
            pointer = entity.currentPointer.coerceIn(0, timeline.size)
        )
    }

    /** Persist [session], its full [timeline] and the [pointer] (rewrites the timeline rows). */
    suspend fun persist(session: DocumentSession, timeline: List<HistoryCommand>, pointer: Int) {
        sessionDao.upsert(session.toEntity(pointer))
        historyDao.deleteBySession(session.sessionId)
        val rows = timeline.mapIndexed { index, command -> command.toEntity(session.sessionId, index) }
        if (rows.isNotEmpty()) historyDao.upsertAll(rows)
    }

    // ─── Mappers ─────────────────────────────────────────────────

    private fun DocumentSession.toEntity(pointer: Int) = DocumentSessionEntity(
        sessionId = sessionId,
        documentId = identity.documentId,
        documentPath = identity.documentPath,
        fileName = identity.fileName,
        fileSize = identity.fileSize,
        lastModified = identity.lastModified,
        sha256 = identity.sha256,
        openedTime = openedTime,
        modifiedTime = modifiedTime,
        editCount = editCount,
        saveState = saveState.name,
        schemaVersion = schemaVersion,
        sessionVersion = sessionVersion,
        currentPointer = pointer,
        historyVersion = DocumentSessionEntity.CURRENT_HISTORY_VERSION
    )

    private fun DocumentSessionEntity.toDomain() = DocumentSession(
        sessionId = sessionId,
        identity = DocumentIdentity(
            documentId = documentId,
            documentPath = documentPath,
            fileName = fileName,
            fileSize = fileSize,
            lastModified = lastModified,
            sha256 = sha256
        ),
        openedTime = openedTime,
        modifiedTime = modifiedTime,
        editCount = editCount,
        saveState = runCatching { SaveState.valueOf(saveState) }.getOrDefault(SaveState.CLEAN),
        schemaVersion = schemaVersion,
        sessionVersion = sessionVersion
    )

    private fun HistoryCommand.toEntity(sessionId: String, orderIndex: Int): HistoryCommandEntity {
        // All commands are PlaceholderCommands in this chunk; fall back gracefully otherwise.
        val placeholder = this as? PlaceholderCommand
        return HistoryCommandEntity(
            commandId = commandId,
            sessionId = sessionId,
            commandType = commandType.name,
            pageIndex = placeholder?.pageIndex ?: -1,
            description = placeholder?.description ?: "",
            timestamp = placeholder?.timestamp ?: System.currentTimeMillis(),
            orderIndex = orderIndex,
            commandVersion = HistoryCommandEntity.CURRENT_COMMAND_VERSION
        )
    }

    private fun HistoryCommandEntity.toCommand(): HistoryCommand = PlaceholderCommand(
        commandType = runCatching { EditOperationType.valueOf(commandType) }
            .getOrDefault(EditOperationType.PAGE_MODIFY),
        pageIndex = pageIndex,
        description = description,
        timestamp = timestamp,
        commandId = commandId
    )
}
