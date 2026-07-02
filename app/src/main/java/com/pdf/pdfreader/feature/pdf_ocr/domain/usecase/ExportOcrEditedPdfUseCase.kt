package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.feature.pdf_ocr.data.engine.OcrEditExporter
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrExportResult
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWordEdit
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import java.io.File
import javax.inject.Inject

/**
 * Saves OCR edits as a new searchable PDF. Non-destructive: the default output
 * is the sibling `<name>_ocr.pdf`, uniquified (`_ocr_2.pdf`, …) instead of
 * silently overwriting; "Save As Copy" passes a custom [customName]. The
 * original file is never modified.
 */
class ExportOcrEditedPdfUseCase @Inject constructor(
    private val repository: OcrRepository,
    private val exporter: OcrEditExporter
) {
    suspend operator fun invoke(
        path: String,
        script: OcrScript,
        edits: List<OcrWordEdit>,
        customName: String? = null
    ): OcrExportResult? {
        val document = repository.cachedDocument(path, script) ?: return null
        val outputPath = resolveOutputPath(path, customName)
        return exporter.export(path, document.pages.values.toList(), edits, outputPath)
    }

    private fun resolveOutputPath(inputPath: String, customName: String?): String {
        val input = File(inputPath)
        val dir = input.parentFile ?: File(".")
        val baseName = customName?.trim()?.removeSuffix(".pdf")?.takeIf { it.isNotEmpty() }
            ?: "${input.nameWithoutExtension}_ocr"

        var candidate = File(dir, "$baseName.pdf")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$counter.pdf")
            counter++
        }
        return candidate.absolutePath
    }
}
