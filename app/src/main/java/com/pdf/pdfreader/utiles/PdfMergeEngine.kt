package com.pdf.pdfreader.utiles

import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges multiple PDFs into one, preserving pages, orientation, bookmarks (document outline)
 * and metadata.
 *
 * Backed by PDFBox's [PDFMergerUtility], which appends each source's page tree and outline to
 * the destination. A temp-file memory setting keeps large merges off the heap. Like the other
 * engines, all work is serialized on a single IO thread because PDFBox is not concurrency-safe
 * here.
 */
@Singleton
class PdfMergeEngine @Inject constructor() {

    companion object {
        private const val TAG = "PdfMergeEngine"
    }

    data class MergeResult(
        val outputPath: String,
        val pageCount: Int,
        val sizeBytes: Long
    )

    /**
     * Merge [inputPaths] in the given order into a single PDF.
     *
     * @param outputPath where to write the result; defaults to `merged_<timestamp>.pdf` next to
     *   the first input.
     * @return the result, or null if fewer than two inputs exist or the merge fails.
     */
    suspend fun merge(
        inputPaths: List<String>,
        outputPath: String? = null
    ): MergeResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val existing = inputPaths.map { File(it) }.filter { it.exists() && it.length() > 0 }
        if (existing.size < 2) {
            Log.w(TAG, "Merge needs at least 2 existing inputs, got ${existing.size}")
            return@withContext null
        }

        val out = outputPath ?: defaultOutputFor(existing.first())
        try {
            val merger = PDFMergerUtility().apply {
                existing.forEach { addSource(it) }
                destinationFileName = out
            }
            // Preserve outlines/metadata (default merge mode) and stream via temp files.
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())

            val file = File(out)
            val pages = PDDocument.load(file).use { it.numberOfPages }
            Log.d(TAG, "Merged ${existing.size} files → $out ($pages pages, ${file.length()} bytes)")
            MergeResult(out, pages, file.length())
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed", e)
            null
        }
    }

    private fun defaultOutputFor(first: File): String {
        val parent = first.parentFile?.absolutePath ?: first.absolutePath
        return "$parent/merged_${System.currentTimeMillis()}.pdf"
    }
}
