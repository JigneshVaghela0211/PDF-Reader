package com.pdf.pdfreader.feature.document_session.domain.usecase

import com.pdf.pdfreader.feature.document_session.domain.command.HistoryCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory undo/redo for editing sessions, modelled as a single chronological **history timeline**
 * plus a pointer (MC11 refinement).
 *
 * Instead of two stacks, each session has one ordered list of every recorded [HistoryCommand] and a
 * `pointer` = how many are currently applied. Undo/redo are *derived* from the pointer:
 * - applied (undoable) commands are `timeline[0 until pointer]`
 * - redoable commands are `timeline[pointer until size]`
 *
 * [undo]/[redo] remain the public API; they just move the pointer (and invoke the command's
 * [HistoryCommand.undo]/[HistoryCommand.redo], which are no-ops for now). Recording a new command
 * truncates the redo tail. Stacks are keyed by sessionId, so operations never leak across documents.
 *
 * Scope: memory only. Persistence of the timeline is handled separately; state here is lost on
 * process death unless restored via [restore].
 */
@Singleton
class SessionUndoRedoEngine @Inject constructor() {

    companion object {
        /** Cap timeline length so long sessions can't grow memory without bound. */
        const val MAX_HISTORY = 100
    }

    private val timelines = mutableMapOf<String, ArrayDeque<HistoryCommand>>()
    private val pointers = mutableMapOf<String, Int>()

    /** Load a persisted timeline + pointer into memory for [sessionId] (restore). */
    @Synchronized
    fun restore(sessionId: String, timeline: List<HistoryCommand>, pointer: Int) {
        val tl = ArrayDeque(timeline)
        timelines[sessionId] = tl
        pointers[sessionId] = pointer.coerceIn(0, tl.size)
    }

    /** Record a new command at the pointer: drop any redo tail, append, advance the pointer. */
    @Synchronized
    fun record(sessionId: String, command: HistoryCommand) {
        val tl = timelines.getOrPut(sessionId) { ArrayDeque() }
        var pointer = pointers[sessionId] ?: 0
        // Truncate the redo tail (anything after the pointer becomes unreachable once a new edit lands).
        while (tl.size > pointer) tl.removeLast()
        tl.addLast(command)
        pointer = tl.size
        // Enforce the cap by dropping the oldest, keeping the pointer consistent.
        while (tl.size > MAX_HISTORY) {
            tl.removeFirst()
            pointer--
        }
        pointers[sessionId] = pointer.coerceAtLeast(0)
    }

    /** Undo the newest applied command of [sessionId]; returns it, or null if nothing to undo. */
    @Synchronized
    fun undo(sessionId: String): HistoryCommand? {
        val tl = timelines[sessionId] ?: return null
        val pointer = pointers[sessionId] ?: 0
        if (pointer <= 0) return null
        val command = tl[pointer - 1]
        command.undo()
        pointers[sessionId] = pointer - 1
        return command
    }

    /** Redo the next command of [sessionId]; returns it, or null if nothing to redo. */
    @Synchronized
    fun redo(sessionId: String): HistoryCommand? {
        val tl = timelines[sessionId] ?: return null
        val pointer = pointers[sessionId] ?: 0
        if (pointer >= tl.size) return null
        val command = tl[pointer]
        command.redo()
        pointers[sessionId] = pointer + 1
        return command
    }

    @Synchronized
    fun canUndo(sessionId: String): Boolean = (pointers[sessionId] ?: 0) > 0

    @Synchronized
    fun canRedo(sessionId: String): Boolean {
        val tl = timelines[sessionId] ?: return false
        return (pointers[sessionId] ?: 0) < tl.size
    }

    /** Number of currently-applied commands (== the pointer) — the session's effective edit count. */
    @Synchronized
    fun appliedCount(sessionId: String): Int = pointers[sessionId] ?: 0

    /** The full chronological timeline (applied + redoable), oldest → newest. */
    @Synchronized
    fun timeline(sessionId: String): List<HistoryCommand> = timelines[sessionId]?.toList() ?: emptyList()

    /** The current pointer (how many commands are applied). */
    @Synchronized
    fun pointer(sessionId: String): Int = pointers[sessionId] ?: 0

    /** Drop all history for [sessionId] (called when the session closes). */
    @Synchronized
    fun clear(sessionId: String) {
        timelines.remove(sessionId)
        pointers.remove(sessionId)
    }
}
