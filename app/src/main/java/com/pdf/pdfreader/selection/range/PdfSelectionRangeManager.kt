package com.pdf.pdfreader.selection.range

import androidx.compose.ui.geometry.Rect
import com.pdf.pdfreader.selection.model.PdfSelectableWord
import com.pdf.pdfreader.selection.model.PdfSelectionRange
import javax.inject.Inject

/**
 * Owns all word-level selection range math: turning handle anchors into the inclusive word range
 * and its bounds. Pure and stateless — the ViewModel holds the state and calls in here.
 *
 * All operations are order-independent, so the start/end handles may cross over each other while
 * being dragged.
 */
class PdfSelectionRangeManager @Inject constructor() {

    /** A single-word selection (the initial long-press result). */
    fun single(word: PdfSelectableWord): PdfSelectionRange =
        PdfSelectionRange(word, word, listOf(word), boundsOf(listOf(word)))

    /** Inclusive range between two anchor words within a reading-ordered [words] list. */
    fun between(words: List<PdfSelectableWord>, a: PdfSelectableWord, b: PdfSelectableWord): PdfSelectionRange? {
        val ai = words.indexOf(a)
        val bi = words.indexOf(b)
        if (ai == -1 || bi == -1) return null
        return of(words.subList(minOf(ai, bi), maxOf(ai, bi) + 1).toList())
    }

    /** Build a range from an already-contiguous, reading-ordered word list. */
    fun of(words: List<PdfSelectableWord>): PdfSelectionRange? {
        if (words.isEmpty()) return null
        return PdfSelectionRange(words.first(), words.last(), words, boundsOf(words))
    }

    private fun boundsOf(words: List<PdfSelectableWord>): Rect = Rect(
        left = words.minOf { it.x },
        top = words.minOf { it.y },
        right = words.maxOf { it.x + it.width },
        bottom = words.maxOf { it.y + it.height }
    )
}
