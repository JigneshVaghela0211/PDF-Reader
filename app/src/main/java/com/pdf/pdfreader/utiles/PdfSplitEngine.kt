package com.pdf.pdfreader.utiles

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Splits a PDF into smaller PDFs. Supports three modes:
 *  1. [splitEveryPage]  — one single-page file per page.
 *  2. [splitRanges]     — one file per requested 1-based inclusive page range (e.g. 1–10, 11–20).
 *  3. [extractPages]    — one file containing an arbitrary set of selected pages.
 *
 * Pages are deep-cloned via [PDDocument.importPage], so each output is self-contained (its own
 * copy of fonts/resources) and keeps the original page orientation. Serialized on one IO thread.
 */
@Singleton
class PdfSplitEngine @Inject constructor() {

    companion object {
        private const val TAG = "PdfSplitEngine"
    }

    data class SplitOutput(
        val outputPath: String,
        /** 1-based inclusive page range that this file covers in the original. */
        val startPage: Int,
        val endPage: Int
    )

    /** Split into N single-page PDFs. */
    suspend fun splitEveryPage(inputPath: String): List<SplitOutput> =
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            val input = File(inputPath)
            if (!input.exists()) return@withContext emptyList()
            try {
                PDDocument.load(input).use { source ->
                    val results = ArrayList<SplitOutput>(source.numberOfPages)
                    for (i in 0 until source.numberOfPages) {
                        coroutineContext.ensureActive()
                        val out = outputFile(input, "p${i + 1}")
                        writeSubset(source, listOf(i), out)
                        results.add(SplitOutput(out.absolutePath, i + 1, i + 1))
                    }
                    results
                }
            } catch (e: Exception) {
                Log.e(TAG, "splitEveryPage failed", e)
                emptyList()
            }
        }

    /**
     * Export each [ranges] entry (1-based inclusive) to its own PDF.
     * Out-of-bounds endpoints are clamped; empty/invalid ranges are skipped.
     */
    suspend fun splitRanges(inputPath: String, ranges: List<IntRange>): List<SplitOutput> =
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            val input = File(inputPath)
            if (!input.exists() || ranges.isEmpty()) return@withContext emptyList()
            try {
                PDDocument.load(input).use { source ->
                    val total = source.numberOfPages
                    val results = ArrayList<SplitOutput>(ranges.size)
                    for (range in ranges) {
                        coroutineContext.ensureActive()
                        val start = range.first.coerceIn(1, total)
                        val end = range.last.coerceIn(start, total)
                        val zeroBased = (start..end).map { it - 1 }
                        if (zeroBased.isEmpty()) continue
                        val out = outputFile(input, "${start}-${end}")
                        writeSubset(source, zeroBased, out)
                        results.add(SplitOutput(out.absolutePath, start, end))
                    }
                    results
                }
            } catch (e: Exception) {
                Log.e(TAG, "splitRanges failed", e)
                emptyList()
            }
        }

    /** Export an arbitrary set of 1-based [pages] into a single PDF, preserving their given order. */
    suspend fun extractPages(inputPath: String, pages: List<Int>): SplitOutput? =
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            val input = File(inputPath)
            if (!input.exists() || pages.isEmpty()) return@withContext null
            try {
                PDDocument.load(input).use { source ->
                    val total = source.numberOfPages
                    val zeroBased = pages.map { it - 1 }.filter { it in 0 until total }
                    if (zeroBased.isEmpty()) return@use null
                    val out = outputFile(input, "selected")
                    writeSubset(source, zeroBased, out)
                    SplitOutput(out.absolutePath, pages.minOrNull() ?: 1, pages.maxOrNull() ?: 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractPages failed", e)
                null
            }
        }

    /** Clone [pageIndices] (0-based) from [source] into a fresh document written to [out]. */
    private fun writeSubset(source: PDDocument, pageIndices: List<Int>, out: File) {
        PDDocument().use { target ->
            for (idx in pageIndices) {
                if (idx in 0 until source.numberOfPages) {
                    target.importPage(source.getPage(idx))
                }
            }
            target.save(out)
        }
    }

    private fun outputFile(input: File, suffix: String): File {
        val parent = input.parentFile ?: input.absoluteFile.parentFile
        return File(parent, "${input.nameWithoutExtension}_$suffix.pdf")
    }
}
