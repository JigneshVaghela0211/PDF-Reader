package com.pdf.pdfreader.domain.usecase

import androidx.compose.ui.geometry.Rect
import com.pdf.pdfreader.domain.model.TextWord
import javax.inject.Inject

/**
 * Pure range math for word-level text selection. Given a reading-ordered word list and two anchor
 * words (the start/end handles), produces the inclusive selected range and its bounding box.
 *
 * Order-independent: the two anchors may cross over each other as handles are dragged.
 */
class SelectionRangeUseCase @Inject constructor() {

    /** Inclusive sublist of [words] between anchors [a] and [b], in reading order. */
    fun rangeBetween(words: List<TextWord>, a: TextWord, b: TextWord): List<TextWord> {
        val ai = words.indexOf(a)
        val bi = words.indexOf(b)
        if (ai == -1 || bi == -1) return emptyList()
        return words.subList(minOf(ai, bi), maxOf(ai, bi) + 1).toList()
    }

    /** Bounding box (normalized 0..1) enclosing [words], or null if empty. */
    fun boundsOf(words: List<TextWord>): Rect? {
        if (words.isEmpty()) return null
        return Rect(
            left = words.minOf { it.x },
            top = words.minOf { it.y },
            right = words.maxOf { it.x + it.width },
            bottom = words.maxOf { it.y + it.height }
        )
    }
}
