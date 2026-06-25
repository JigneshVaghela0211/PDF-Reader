package com.pdf.pdfreader.utiles

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Page-level structural editing for a PDF: insert (blank / from another file / duplicate),
 * reorder and delete pages — then write the real new page order to disk.
 *
 * Every operation is **non-destructive**: it assembles a fresh document by deep-cloning the
 * required pages ([PDDocument.importPage], which copies each page's resources) in the target
 * order and saves it as a sibling `*_pages.pdf`, leaving the original untouched. Blank pages are
 * created at the source document's own page size so inserts match the surrounding pages.
 *
 * All work is serialized on a single IO thread (PDFBox is not concurrency-safe here).
 */
@Singleton
class PdfPageManager @Inject constructor() {

    companion object {
        private const val TAG = "PdfPageManager"
    }

    data class PageResult(val outputPath: String, val pageCount: Int)

    /**
     * Rebuild the document with pages in [newOrderZeroBased] order (a list of original 0-based
     * page indices). Indices out of range are dropped; the list may also omit pages (acting as a
     * combined reorder+delete) or repeat them (reorder+duplicate).
     */
    suspend fun reorder(
        inputPath: String,
        newOrderZeroBased: List<Int>,
        outputPath: String? = null
    ): PageResult? = build(inputPath, outputPath) { src ->
        newOrderZeroBased.filter { it in 0 until src.numberOfPages }
    }

    /** Insert a blank page at [atIndex] (0-based; clamped to [0, pageCount]). */
    suspend fun insertBlankPage(
        inputPath: String,
        atIndex: Int,
        outputPath: String? = null
    ): PageResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        runOp(inputPath, outputPath) { src, target ->
            val n = src.numberOfPages
            val insertAt = atIndex.coerceIn(0, n)
            val blankSize = src.takeIf { n > 0 }?.getPage(0)?.mediaBox ?: PDRectangle.A4
            for (i in 0 until n) {
                if (i == insertAt) target.addPage(PDPage(blankSize))
                target.importPage(src.getPage(i))
            }
            if (insertAt >= n) target.addPage(PDPage(blankSize))
        }
    }

    /** Duplicate [pageIndex]; the copy lands at [atIndex] (default: right after the original). */
    suspend fun duplicatePage(
        inputPath: String,
        pageIndex: Int,
        atIndex: Int? = null,
        outputPath: String? = null
    ): PageResult? = build(inputPath, outputPath) { src ->
        if (pageIndex !in 0 until src.numberOfPages) return@build (0 until src.numberOfPages).toList()
        val order = (0 until src.numberOfPages).toMutableList()
        order.add((atIndex ?: (pageIndex + 1)).coerceIn(0, order.size), pageIndex)
        order
    }

    /** Delete [pageIndex] (0-based). No-op (full copy) if out of range. */
    suspend fun deletePage(
        inputPath: String,
        pageIndex: Int,
        outputPath: String? = null
    ): PageResult? = build(inputPath, outputPath) { src ->
        (0 until src.numberOfPages).filter { it != pageIndex }
    }

    /**
     * Insert [sourcePagesZeroBased] from [sourcePath] into [inputPath] at [atIndex]. Pages are
     * cloned, so the imported pages keep their own size/orientation/resources.
     */
    suspend fun importPagesFrom(
        inputPath: String,
        sourcePath: String,
        sourcePagesZeroBased: List<Int>,
        atIndex: Int,
        outputPath: String? = null
    ): PageResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val input = File(inputPath)
        val other = File(sourcePath)
        if (!input.exists() || !other.exists()) return@withContext null
        val out = outputPath ?: defaultOutput(input)
        try {
            PDDocument.load(input).use { src ->
                PDDocument.load(other).use { donor ->
                    PDDocument().use { target ->
                        val n = src.numberOfPages
                        val insertAt = atIndex.coerceIn(0, n)
                        for (i in 0 until insertAt) target.importPage(src.getPage(i))
                        sourcePagesZeroBased
                            .filter { it in 0 until donor.numberOfPages }
                            .forEach { target.importPage(donor.getPage(it)) }
                        for (i in insertAt until n) target.importPage(src.getPage(i))
                        target.save(File(out))
                        PageResult(out, target.numberOfPages)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "importPagesFrom failed", e)
            null
        }
    }

    /** Shared assembler: caller returns the ordered 0-based source indices to clone. */
    private suspend fun build(
        inputPath: String,
        outputPath: String?,
        order: (PDDocument) -> List<Int>
    ): PageResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        runOp(inputPath, outputPath) { src, target ->
            order(src).forEach { idx ->
                if (idx in 0 until src.numberOfPages) target.importPage(src.getPage(idx))
            }
        }
    }

    private inline fun runOp(
        inputPath: String,
        outputPath: String?,
        crossinline body: (src: PDDocument, target: PDDocument) -> Unit
    ): PageResult? {
        val input = File(inputPath)
        if (!input.exists()) return null
        val out = outputPath ?: defaultOutput(input)
        return try {
            PDDocument.load(input).use { src ->
                PDDocument().use { target ->
                    body(src, target)
                    if (target.numberOfPages == 0) {
                        Log.w(TAG, "Operation produced an empty document; aborting")
                        return null
                    }
                    target.save(File(out))
                    PageResult(out, target.numberOfPages)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Page operation failed", e)
            null
        }
    }

    private fun defaultOutput(input: File): String {
        val parent = input.parentFile?.absolutePath ?: input.absolutePath
        return "$parent/${input.nameWithoutExtension}_pages.pdf"
    }
}
