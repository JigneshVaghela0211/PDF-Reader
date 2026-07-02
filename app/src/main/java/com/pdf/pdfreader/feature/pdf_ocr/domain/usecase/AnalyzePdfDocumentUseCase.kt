package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.feature.pdf_ocr.data.engine.PdfDocumentAnalyzer
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PdfDocumentClassification
import javax.inject.Inject

/** Classifies a PDF as SEARCHABLE / SCANNED / MIXED for the OCR decision flow. */
class AnalyzePdfDocumentUseCase @Inject constructor(
    private val analyzer: PdfDocumentAnalyzer
) {
    suspend operator fun invoke(filePath: String): PdfDocumentClassification =
        analyzer.analyze(filePath)
}
