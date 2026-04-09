package com.pdf.pdfreader.domain.model

/**
 * Strict interaction mode enum.
 * Only ONE mode can be active at any time.
 * Mode switches are explicit, driven by user gesture events.
 *
 * Priority order (highest → lowest):
 *   RESIZE > DRAG > EDIT_TEXT > TEXT_NOTE > DRAW > NONE
 *
 * When mode != NONE, parent scroll MUST be disabled.
 */
enum class InteractionMode {
    /** No interaction — PDF scroll is enabled */
    NONE,
    /** Pen / Highlighter / Eraser stroke in progress */
    DRAW,
    /** Image or text-note drag in progress */
    DRAG,
    /** Image resize-handle drag in progress */
    RESIZE,
    /** Inline text-block editor is open */
    EDIT_TEXT,
    /** Movable text-note interaction (tap / drag) */
    TEXT_NOTE,
    /** Granular text selection in progress */
    SELECT_TEXT
}
