package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrProgress
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Runs OCR on a whole document with per-page progress; cached pages are skipped (resume). */
class RecognizeDocumentUseCase @Inject constructor(
    private val repository: OcrRepository
) {
    operator fun invoke(
        path: String,
        script: OcrScript,
        force: Boolean = false
    ): Flow<OcrProgress> = repository.recognizeDocument(path, script, force)
}
