package com.pdf.pdfreader.selection.model

import com.pdf.pdfreader.domain.model.TextWord

/**
 * Micro Chunk 3: the ONLY output of the inline selection editor. It captures the user's intent to
 * change a selected span's text — it does **not** perform any replacement. A later micro-chunk
 * consumes this (analyzer → region/OCR replacement); until then it is purely recorded in UI state.
 *
 * Pure data (normalized word geometry, no Android/PDFBox), so it can flow straight into the future
 * replacement path without carrying UI concerns.
 */
data class SelectionEditRequest(
    val pageIndex: Int,
    /** The selected words (normalized bounds) at confirm time — the region to edit later. */
    val words: List<TextWord>,
    val originalText: String,
    val newText: String
)
