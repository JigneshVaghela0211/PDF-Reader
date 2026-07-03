package com.pdf.pdfreader.feature.document_session.domain.model

/**
 * An in-progress editing session for one open PDF.
 *
 * Created when a PDF is opened and closed when it is dismissed. It is the anchor future editing work
 * (undo/redo, persistence, save) will hang off — but this chunk only creates, tracks and closes it;
 * there is no history, persistence or save yet.
 *
 * @property sessionId     unique per open (a UUID).
 * @property identity      the [DocumentIdentity] this session edits (name/size/mtime, not just path).
 * @property openedTime    epoch millis when the session was created.
 * @property modifiedTime  epoch millis of the most recent tracked edit (== [openedTime] when clean).
 * @property editCount     number of edits recorded so far.
 * @property saveState     current [SaveState].
 * @property schemaVersion placeholder — version of the persisted session schema (future).
 * @property sessionVersion placeholder — version of this session's data shape (future).
 */
data class DocumentSession(
    val sessionId: String,
    val identity: DocumentIdentity,
    val openedTime: Long,
    val modifiedTime: Long,
    val editCount: Int = 0,
    val saveState: SaveState = SaveState.CLEAN,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionVersion: Int = CURRENT_SESSION_VERSION
) {
    /** Convenience passthrough — the underlying document path. */
    val documentPath: String get() = identity.documentPath

    companion object {
        /** Placeholder version stamps; bump when the respective shape changes (future work). */
        const val CURRENT_SCHEMA_VERSION = 1
        const val CURRENT_SESSION_VERSION = 1
    }
}
