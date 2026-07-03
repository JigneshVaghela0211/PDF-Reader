package com.pdf.pdfreader.feature.document_session.domain.model

import java.util.UUID

/**
 * A single edit recorded against a [DocumentSession].
 *
 * Foundation only — this is a plain description of an edit. It is NOT persisted and is NOT wired to
 * undo/redo in this chunk; it exists so the session can count/track edits and so later work has a
 * stable shape to build history on.
 */
data class EditOperation(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: EditOperationType,
    val pageIndex: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** Short human-readable summary (e.g. "Replace 'GOHIL' -> 'PATEL'"). */
    val description: String = ""
)
