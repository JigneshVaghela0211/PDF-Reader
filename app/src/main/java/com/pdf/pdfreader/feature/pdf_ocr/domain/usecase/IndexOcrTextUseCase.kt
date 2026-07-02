package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.data.local.PdfDao
import com.pdf.pdfreader.data.local.PdfTextSnippet
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import javax.inject.Inject

/**
 * Feeds recognized OCR text into the existing Room FTS index (`pdf_text_search`)
 * so in-app search finds scanned documents without opening the `_ocr.pdf` copy.
 * Only the OCR'd pages are refreshed — snippets PdfTextExtractor indexed for the
 * text pages of a mixed document stay intact. Gated by ENABLE_OCR_SEARCH.
 */
class IndexOcrTextUseCase @Inject constructor(
    private val repository: OcrRepository,
    private val pdfDao: PdfDao
) {
    suspend operator fun invoke(path: String, script: OcrScript) {
        if (!PdfEditorFeatureConfig.ENABLE_OCR_SEARCH) return
        val document = repository.cachedDocument(path, script) ?: return

        val snippets = document.pages.values.mapNotNull { page ->
            val text = page.blocks.joinToString("\n") { it.text }.trim()
            if (text.isEmpty()) null else PdfTextSnippet(path, page.pageIndex, text)
        }
        if (snippets.isEmpty()) return

        pdfDao.deleteTextSnippetsForPages(path, snippets.map { it.pageIndex })
        pdfDao.insertTextSnippets(snippets)
    }
}
