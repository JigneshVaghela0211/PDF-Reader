package com.pdf.pdfreader.feature.document_session.domain.model

/**
 * The save status of a [DocumentSession].
 *
 * Foundation only — this chunk merely *tracks* the state; it performs no persistence, no undo/redo
 * and no save. Transitions are driven by higher layers as editing is wired up later.
 */
enum class SaveState {
    /** Freshly opened, no edits applied yet. */
    CLEAN,

    /** Has unsaved edits. */
    MODIFIED,

    /** All edits have been written to disk. */
    SAVED,

    /** The last save attempt failed. */
    ERROR
}
