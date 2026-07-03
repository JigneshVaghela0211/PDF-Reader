package com.pdf.pdfreader.selection.model

import com.pdf.pdfreader.domain.model.TextWord

/**
 * A single word's in-editor preview edit — the data behind the dedicated PreviewLayer.
 *
 * It is deliberately NOT a [com.pdf.pdfreader.domain.model.EditedTextBlock]: it never touches the
 * PDF, the export path, or the replacement engine. It only says "render [newText] over [word]'s
 * position and visually hide the original." Removed on Cancel.
 */
data class WordPreviewEdit(
    val pageIndex: Int,
    val word: TextWord,
    val newText: String
)
