package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.util.Log
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PageTextKind
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PdfDocumentClassification
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PdfDocumentClassification.DocKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Classifies a PDF as SEARCHABLE / SCANNED / MIXED by checking every page for
 * selectable text (PDFTextStripper) and, failing that, for image XObjects.
 *
 * Used by the OCR decision flow: real text editing for pages with text, the OCR
 * pipeline for image-only pages. Analysis failures (missing file, encrypted or
 * corrupt document) fall back to SEARCHABLE so the existing editing path keeps
 * behaving exactly as before.
 */
@Singleton
class PdfDocumentAnalyzer @Inject constructor() {

    companion object {
        private const val TAG = "PdfDocumentAnalyzer"

        /**
         * Minimum non-whitespace characters for a page to count as TEXT. Filters out
         * scans that carry a stray watermark/page-number text object but are otherwise
         * image-only.
         */
        private const val MIN_TEXT_CHARS = 20
    }

    suspend fun analyze(filePath: String): PdfDocumentClassification =
        withContext(Dispatchers.IO) {
            val fallback = PdfDocumentClassification(DocKind.SEARCHABLE, emptyList())
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext fallback

                PDDocument.load(file).use { document ->
                    if (document.isEncrypted) return@use fallback

                    val stripper = PDFTextStripper()
                    val pages = (0 until document.numberOfPages).map { pageIdx ->
                        coroutineContext.ensureActive()
                        classifyPage(document, stripper, pageIdx)
                    }

                    val hasText = pages.any { it == PageTextKind.TEXT }
                    val hasImageOnly = pages.any { it == PageTextKind.IMAGE_ONLY }
                    val kind = when {
                        hasImageOnly && hasText -> DocKind.MIXED
                        hasImageOnly -> DocKind.SCANNED
                        else -> DocKind.SEARCHABLE
                    }
                    PdfDocumentClassification(kind, pages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze $filePath", e)
                fallback
            }
        }

    private fun classifyPage(
        document: PDDocument,
        stripper: PDFTextStripper,
        pageIdx: Int
    ): PageTextKind {
        val textChars = try {
            stripper.startPage = pageIdx + 1
            stripper.endPage = pageIdx + 1
            stripper.getText(document).count { !it.isWhitespace() }
        } catch (e: Exception) {
            Log.w(TAG, "Text extraction failed on page $pageIdx", e)
            0
        }
        return when {
            textChars >= MIN_TEXT_CHARS -> PageTextKind.TEXT
            hasImageXObject(document.getPage(pageIdx)) -> PageTextKind.IMAGE_ONLY
            else -> PageTextKind.EMPTY
        }
    }

    private fun hasImageXObject(page: PDPage): Boolean = try {
        val resources = page.resources
        resources != null && resources.xObjectNames.any { resources.isImageXObject(it) }
    } catch (e: Exception) {
        false
    }
}
