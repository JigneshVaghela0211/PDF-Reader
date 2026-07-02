package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.util.Log
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.MakeSearchableResult
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrDocument
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Writes recognized OCR text into a PDF as an **invisible** layer (rendering mode
 * NEITHER): the raster stays pixel-identical while the hidden glyphs make the text
 * selectable / searchable / copyable.
 *
 * Coordinates and text rotation go through [OcrCoordinateMapper], so pages with a
 * /Rotate entry are handled correctly (unlike the retired SearchablePdfEngine).
 *
 * Fonts come from [OcrFontProvider]: WinAnsi text uses HELVETICA; other scripts
 * embed a subsetted system Noto font so non-Latin text is genuinely searchable
 * (sanitized to `?` only when no usable system font exists).
 * [writeInvisibleWords]'s `exclude` lets the OCR-edit exporter skip words it
 * replaces with visible text.
 */
@Singleton
class SearchableLayerWriter @Inject constructor(
    private val fontProvider: OcrFontProvider
) {

    companion object {
        private const val TAG = "SearchableLayerWriter"
    }

    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Write all recognized pages of [document] into a sibling `*_ocr.pdf`. */
    suspend fun writeSearchablePdf(
        inputPath: String,
        document: OcrDocument,
        outputPath: String? = null
    ): MakeSearchableResult? = withContext(pdfDispatcher) {
        val input = File(inputPath)
        if (!input.exists() || input.length() == 0L) return@withContext null
        val out = outputPath ?: defaultOutput(input)

        try {
            PDDocument.load(input).use { doc ->
                var pagesProcessed = 0
                var wordsAdded = 0
                for ((pageIndex, ocrPage) in document.pages) {
                    if (pageIndex >= doc.numberOfPages) continue
                    coroutineContext.ensureActive()
                    val written = writeInvisibleWords(doc, doc.getPage(pageIndex), ocrPage)
                    if (written > 0) {
                        wordsAdded += written
                        pagesProcessed++
                    }
                }
                doc.save(File(out))
                MakeSearchableResult(out, pagesProcessed, wordsAdded)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Failed to write searchable PDF for $inputPath", e)
            null
        }
    }

    /**
     * Append invisible text for every word of [ocrPage] onto [pdPage]; returns the
     * number of words written. Words in [exclude] are skipped (they'll be drawn
     * visibly by the OCR-edit exporter instead).
     */
    fun writeInvisibleWords(
        doc: PDDocument,
        pdPage: PDPage,
        ocrPage: OcrPage,
        exclude: Set<OcrWord> = emptySet()
    ): Int {
        val cropBox = pdPage.cropBox
        var written = 0
        PDPageContentStream(doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (word in ocrPage.words) {
                if (word in exclude) continue
                val anchor = OcrCoordinateMapper.toPdfAnchor(
                    rect = OcrCoordinateMapper.NormalizedRect(word.x, word.y, word.width, word.height),
                    cropWidth = cropBox.width,
                    cropHeight = cropBox.height,
                    cropOffsetX = cropBox.lowerLeftX,
                    cropOffsetY = cropBox.lowerLeftY,
                    rotation = ocrPage.pageRotation
                )
                val font = fontProvider.fontFor(doc, ocrPage.script, word.text)
                    ?: PDType1Font.HELVETICA
                cs.beginText()
                cs.setRenderingMode(RenderingMode.NEITHER) // invisible
                cs.setFont(font, anchor.fontSize)
                if (anchor.rotationDegrees != 0) {
                    cs.setTextMatrix(
                        Matrix.getRotateInstance(
                            Math.toRadians(anchor.rotationDegrees.toDouble()), anchor.x, anchor.y
                        )
                    )
                } else {
                    cs.newLineAtOffset(anchor.x, anchor.y)
                }
                showSafely(cs, word.text)
                cs.endText()
                written++
            }
        }
        return written
    }

    /** HELVETICA only covers WinAnsi; replace anything it can't encode so showText never throws. */
    private fun showSafely(cs: PDPageContentStream, text: String) {
        try {
            cs.showText(text)
        } catch (_: Exception) {
            try { cs.showText(text.replace(Regex("[^\\x20-\\x7E]"), "?")) } catch (_: Exception) {}
        }
    }

    private fun defaultOutput(input: File): String {
        val parent = input.parentFile?.absolutePath ?: input.absolutePath
        return "$parent/${input.nameWithoutExtension}_ocr.pdf"
    }
}
