package com.pdf.pdfreader.feature.pdf_ocr.domain.repository

import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrDocument
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrProgress
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import kotlinx.coroutines.flow.Flow

/**
 * Cache-first access to OCR results. Presentation reaches recognition only
 * through use cases → this repository → the engine.
 */
interface OcrRepository {

    /** Recognize (or load from cache) a single page. */
    suspend fun recognizePage(
        path: String,
        pageIndex: Int,
        script: OcrScript,
        force: Boolean = false
    ): OcrPage

    /**
     * Recognize the whole document, skipping already-cached pages (resume) unless
     * [force]. Each finished page is persisted immediately, so cancellation loses
     * nothing. Collect the returned flow for progress.
     */
    fun recognizeDocument(
        path: String,
        script: OcrScript,
        force: Boolean = false
    ): Flow<OcrProgress>

    /** Everything cached for this document+script, or null when nothing is cached. */
    suspend fun cachedDocument(path: String, script: OcrScript): OcrDocument?

    /** Drop all cached OCR results for this document. */
    suspend fun invalidate(path: String)
}
