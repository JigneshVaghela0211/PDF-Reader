package com.pdf.pdfreader.feature.document_session.domain.command

import com.pdf.pdfreader.feature.document_session.domain.model.EditOperationType

/**
 * A single reversible edit in a session's history (MC10 command-based model).
 *
 * The undo/redo engine stores `HistoryCommand`s rather than plain records, so each entry owns its
 * own [execute]/[undo]/[redo]. Concrete behaviour is NOT implemented yet — current commands are
 * lightweight placeholders (see [PlaceholderCommand]); this chunk only reshapes the history model so
 * real, effectful commands can drop in later without touching the engine.
 */
interface HistoryCommand {
    /** Stable unique id for this command instance. */
    val commandId: String

    /** What kind of edit this command represents. */
    val commandType: EditOperationType

    /** Apply the edit. */
    fun execute()

    /** Reverse the edit. */
    fun undo()

    /** Re-apply the edit after an undo. */
    fun redo()
}
