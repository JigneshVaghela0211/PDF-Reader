package com.pdf.pdfreader.feature.document_session.domain.command

import com.pdf.pdfreader.feature.document_session.domain.model.EditOperationType
import java.util.UUID

/**
 * A lightweight [HistoryCommand] with NO behaviour yet (MC10).
 *
 * It carries the command's identity and descriptive metadata (type, page, description, timestamp) so
 * the history engine — and later Room persistence — has something concrete to store, but
 * [execute]/[undo]/[redo] are intentionally empty. Real, effectful commands replace these later
 * without changing the engine or the history model.
 */
data class PlaceholderCommand(
    override val commandType: EditOperationType,
    val pageIndex: Int,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    override val commandId: String = UUID.randomUUID().toString()
) : HistoryCommand {
    override fun execute() { /* no behaviour yet (MC10 placeholder) */ }
    override fun undo() { /* no behaviour yet (MC10 placeholder) */ }
    override fun redo() { /* no behaviour yet (MC10 placeholder) */ }
}
