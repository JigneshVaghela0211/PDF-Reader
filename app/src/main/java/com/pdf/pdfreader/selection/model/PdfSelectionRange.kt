package com.pdf.pdfreader.selection.model

import androidx.compose.ui.geometry.Rect

/**
 * An immutable word-level text selection: the two anchor words the start/end handles map to, the
 * inclusive list of words between them (in reading order), and their combined bounds (normalized
 * 0..1). [startWord] is always `words.first()` and [endWord] always `words.last()`.
 */
data class PdfSelectionRange(
    val startWord: PdfSelectableWord,
    val endWord: PdfSelectableWord,
    val words: List<PdfSelectableWord>,
    val bounds: Rect
)
