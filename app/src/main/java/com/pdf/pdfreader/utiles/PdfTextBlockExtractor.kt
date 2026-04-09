package com.pdf.pdfreader.utiles

import android.util.Log
import com.pdf.pdfreader.domain.model.TextBlock
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Extracts text blocks with precise coordinates from PDF pages using PdfBox.
 *
 * Text positions are grouped into logical blocks and returned with normalized
 * coordinates (0..1) relative to the page dimensions, making them resolution-independent.
 */
@Singleton
class PdfTextBlockExtractor @Inject constructor() {

    companion object {
        private const val TAG = "PdfTextBlockExtractor"

        /** Horizontal gap threshold (in PDF points) to start a new word */
        private const val WORD_GAP_THRESHOLD = 5f

        /** Vertical gap threshold (in PDF points) to start a new line/block */
        private const val LINE_GAP_THRESHOLD = 3f

        /** Minimum text length to qualify as a block */
        private const val MIN_BLOCK_LENGTH = 1
    }

    /**
     * Extract text blocks from all pages of a PDF.
     *
     * @param filePath Path to the PDF file
     * @return Map of page index → list of text blocks on that page
     */
    suspend fun extractTextBlocks(filePath: String): Map<Int, List<TextBlock>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Int, List<TextBlock>>()

            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext result

                PDDocument.load(file).use { document ->
                    if (document.isEncrypted) return@use

                    for (pageIdx in 0 until document.numberOfPages) {
                        coroutineContext.ensureActive()

                        val page = document.getPage(pageIdx)
                        val cropBox = page.cropBox
                        val pdfWidth = cropBox.width
                        val pdfHeight = cropBox.height

                        val positions = mutableListOf<CharPosition>()

                        val stripper = object : PDFTextStripper() {
                            override fun writeString(
                                text: String,
                                textPositions: List<TextPosition>
                            ) {
                                for (tp in textPositions) {
                                    val char = tp.unicode ?: continue
                                    if (char.isBlank() && char != " ") continue

                                    positions.add(
                                        CharPosition(
                                            char = char,
                                            x = tp.xDirAdj,
                                            y = tp.yDirAdj - tp.heightDir,
                                            width = tp.widthDirAdj,
                                            height = tp.heightDir,
                                            fontSize = tp.fontSize,
                                            fontName = tp.font?.name ?: "unknown"
                                        )
                                    )
                                }
                            }
                        }

                        stripper.startPage = pageIdx + 1
                        stripper.endPage = pageIdx + 1
                        stripper.sortByPosition = true

                        try {
                            stripper.getText(document)
                        } catch (e: Exception) {
                            Log.w(TAG, "Text extraction failed for page $pageIdx", e)
                            continue
                        }

                        if (positions.isEmpty()) continue

                        // Group character positions into text blocks
                        val blocks = groupIntoBlocks(positions, pageIdx, pdfWidth, pdfHeight)
                        if (blocks.isNotEmpty()) {
                            result[pageIdx] = blocks
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract text blocks from $filePath", e)
            }

            result
        }

    /**
     * Group individual character positions into logical text blocks.
     * Characters on the same line (similar Y) with small X gaps form words/lines.
     * Lines with similar X alignment and small Y gaps form blocks.
     */
    private fun groupIntoBlocks(
        positions: List<CharPosition>,
        pageIndex: Int,
        pdfWidth: Float,
        pdfHeight: Float
    ): List<TextBlock> {
        if (positions.isEmpty()) return emptyList()

        // Step 1: Group into lines based on Y position
        val lines = mutableListOf<MutableList<CharPosition>>()
        var currentLine = mutableListOf(positions.first())

        for (i in 1 until positions.size) {
            val prev = positions[i - 1]
            val curr = positions[i]

            val yDiff = kotlin.math.abs(curr.y - prev.y)

            if (yDiff > prev.height * 0.5f) {
                // New line
                lines.add(currentLine)
                currentLine = mutableListOf(curr)
            } else {
                currentLine.add(curr)
            }
        }
        lines.add(currentLine)

        // Step 2: Group adjacent lines into blocks
        val blocks = mutableListOf<TextBlock>()

        var blockLines = mutableListOf(lines.first())

        for (i in 1 until lines.size) {
            val prevLine = blockLines.last()
            val currLine = lines[i]

            val prevBottom = prevLine.maxOf { it.y + it.height }
            val currTop = currLine.minOf { it.y }
            val avgPrevHeight = prevLine.map { it.height }.average().toFloat()

            val gap = currTop - prevBottom

            if (gap < avgPrevHeight * 1.5f && gap >= -avgPrevHeight * 0.5f) {
                // Same block — lines are close together
                blockLines.add(currLine)
            } else {
                // Finalize previous block
                createTextBlock(blockLines, pageIndex, pdfWidth, pdfHeight)?.let { blocks.add(it) }
                blockLines = mutableListOf(currLine)
            }
        }

        // Finalize last block
        createTextBlock(blockLines, pageIndex, pdfWidth, pdfHeight)?.let { blocks.add(it) }

        return blocks
    }

    /**
     * Create a TextBlock from a group of character-position lines.
     */
    private fun createTextBlock(
        lines: List<List<CharPosition>>,
        pageIndex: Int,
        pdfWidth: Float,
        pdfHeight: Float
    ): TextBlock? {
        val allChars = lines.flatten()
        if (allChars.isEmpty()) return null

        // Build text by joining lines
        val textBuilder = StringBuilder()
        val words = mutableListOf<com.pdf.pdfreader.domain.model.TextWord>()
        var currentWordChars = mutableListOf<CharPosition>()
        
        fun commitWord() {
            if (currentWordChars.isEmpty()) return
            val wMinX = currentWordChars.minOf { it.x }
            val wMinY = currentWordChars.minOf { it.y }
            val wMaxX = currentWordChars.maxOf { it.x + it.width }
            val wMaxY = currentWordChars.maxOf { it.y + it.height }
            val wText = currentWordChars.joinToString("") { it.char }
            words.add(com.pdf.pdfreader.domain.model.TextWord(
                text = wText,
                x = (wMinX / pdfWidth).coerceIn(0f, 1f),
                y = (wMinY / pdfHeight).coerceIn(0f, 1f),
                width = ((wMaxX - wMinX) / pdfWidth).coerceIn(0f, 1f),
                height = ((wMaxY - wMinY) / pdfHeight).coerceIn(0f, 1f)
            ))
            currentWordChars.clear()
        }

        for ((lineIdx, line) in lines.withIndex()) {
            for ((charIdx, cp) in line.withIndex()) {
                if (charIdx > 0) {
                    val prev = line[charIdx - 1]
                    val gap = cp.x - (prev.x + prev.width)
                    if (gap > WORD_GAP_THRESHOLD) {
                        textBuilder.append(' ')
                        commitWord()
                    }
                }
                textBuilder.append(cp.char)
                currentWordChars.add(cp)
            }
            commitWord()
            if (lineIdx < lines.size - 1) {
                textBuilder.append('\n')
            }
        }

        val text = textBuilder.toString().trim()
        if (text.length < MIN_BLOCK_LENGTH) return null

        // Calculate bounding box
        val minX = allChars.minOf { it.x }
        val minY = allChars.minOf { it.y }
        val maxX = allChars.maxOf { it.x + it.width }
        val maxY = allChars.maxOf { it.y + it.height }

        // Average font size
        val avgFontSize = allChars.map { it.fontSize }.average().toFloat()

        // Most common font
        val fontName = allChars.groupBy { it.fontName }
            .maxByOrNull { it.value.size }?.key ?: "Helvetica"

        // Normalize to 0..1
        return TextBlock(
            pageIndex = pageIndex,
            text = text,
            x = (minX / pdfWidth).coerceIn(0f, 1f),
            y = (minY / pdfHeight).coerceIn(0f, 1f),
            width = ((maxX - minX) / pdfWidth).coerceIn(0f, 1f),
            height = ((maxY - minY) / pdfHeight).coerceIn(0f, 1f),
            fontSize = avgFontSize,
            fontName = fontName,
            words = words
        )
    }

    /**
     * Internal data class for individual character positions.
     */
    private data class CharPosition(
        val char: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val fontName: String
    )
}
