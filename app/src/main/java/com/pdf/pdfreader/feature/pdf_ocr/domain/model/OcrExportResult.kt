package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/** Outcome of exporting OCR edits into a new searchable PDF. */
data class OcrExportResult(
    val outputPath: String,
    val editsApplied: Int,
    val invisibleWordsAdded: Int
)
