package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import javax.inject.Inject

/** Runs OCR on one page (cache-first). */
class RecognizePageUseCase @Inject constructor(
    private val repository: OcrRepository
) {
    suspend operator fun invoke(
        path: String,
        pageIndex: Int,
        script: OcrScript,
        force: Boolean = false
    ): OcrPage = repository.recognizePage(path, pageIndex, script, force)
}
