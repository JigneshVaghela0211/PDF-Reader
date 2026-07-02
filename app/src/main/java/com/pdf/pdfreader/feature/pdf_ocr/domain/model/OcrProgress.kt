package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/** Fractional progress of a document OCR run ("Page 3 of 25 · 12%"). */
data class OcrProgress(
    /** 1-based index of the page just finished. */
    val currentPage: Int,
    val totalPages: Int,
    val fraction: Float,
    val wordsSoFar: Int,
    /** True when this page was served from the cache (resume) instead of re-recognized. */
    val fromCache: Boolean
)

/** One step of a document OCR run: progress plus the recognized page (null for skipped/failed pages). */
data class OcrRecognitionEvent(
    val progress: OcrProgress,
    val page: OcrPage?
)
