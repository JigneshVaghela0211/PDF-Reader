package com.pdf.pdfreader.feature.document_session.domain.usecase

import com.pdf.pdfreader.feature.document_session.domain.command.HistoryCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory undo/redo for editing sessions (MC10 — command-based history).
 *
 * Each edit records a [HistoryCommand]; undo pops the newest command, calls its [HistoryCommand.undo]
 * and moves it to the redo stack; redo does the reverse via [HistoryCommand.redo]. (Commands are
 * placeholders for now, so those calls are no-ops — the wiring is what matters.) Stacks are **keyed
 * by sessionId**, so undo/redo only ever touch the current editing session and never leak across
 * documents.
 *
 * Scope: memory only — no Room persistence, no save, no export, no recovery. State is lost when the
 * process dies, by design.
 */
@Singleton
class SessionUndoRedoEngine @Inject constructor() {

    companion object {
        /** Cap per-session history so long sessions can't grow memory without bound. */
        const val MAX_HISTORY = 100
    }

    private val undoStacks = mutableMapOf<String, ArrayDeque<HistoryCommand>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<HistoryCommand>>()

    /** Record a new command: push onto the session's undo stack and invalidate its redo stack. */
    @Synchronized
    fun record(sessionId: String, command: HistoryCommand) {
        val undo = undoStacks.getOrPut(sessionId) { ArrayDeque() }
        undo.addLast(command)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redoStacks[sessionId]?.clear()
    }

    /** Undo the newest command of [sessionId]; returns it, or null if nothing to undo. */
    @Synchronized
    fun undo(sessionId: String): HistoryCommand? {
        val command = undoStacks[sessionId]?.removeLastOrNull() ?: return null
        command.undo()
        redoStacks.getOrPut(sessionId) { ArrayDeque() }.addLast(command)
        return command
    }

    /** Redo the most-recently-undone command of [sessionId]; returns it, or null if none. */
    @Synchronized
    fun redo(sessionId: String): HistoryCommand? {
        val command = redoStacks[sessionId]?.removeLastOrNull() ?: return null
        command.redo()
        undoStacks.getOrPut(sessionId) { ArrayDeque() }.addLast(command)
        return command
    }

    @Synchronized
    fun canUndo(sessionId: String): Boolean = !undoStacks[sessionId].isNullOrEmpty()

    @Synchronized
    fun canRedo(sessionId: String): Boolean = !redoStacks[sessionId].isNullOrEmpty()

    /** Number of currently-applied commands (undo-stack depth) — the session's effective edit count. */
    @Synchronized
    fun appliedCount(sessionId: String): Int = undoStacks[sessionId]?.size ?: 0

    /** Snapshot of the undo stack, oldest → newest. */
    @Synchronized
    fun undoHistory(sessionId: String): List<HistoryCommand> = undoStacks[sessionId]?.toList() ?: emptyList()

    /** Snapshot of the redo stack, oldest → newest. */
    @Synchronized
    fun redoHistory(sessionId: String): List<HistoryCommand> = redoStacks[sessionId]?.toList() ?: emptyList()

    /** Drop all history for [sessionId] (called when the session closes). */
    @Synchronized
    fun clear(sessionId: String) {
        undoStacks.remove(sessionId)
        redoStacks.remove(sessionId)
    }
}
