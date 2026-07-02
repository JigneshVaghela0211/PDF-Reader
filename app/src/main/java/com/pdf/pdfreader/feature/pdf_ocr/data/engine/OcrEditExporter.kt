package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrExportResult
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWordEdit
import com.pdf.pdfreader.utiles.PdfPageRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Exports OCR word edits into a new searchable PDF:
 *
 * - **Unedited words** → invisible text layer ([SearchableLayerWriter], real fonts
 *   via [OcrFontProvider] so non-Latin scripts are genuinely searchable).
 * - **Edited words** → a background-colored patch (median-sampled from the
 *   rendered page around the word) covering the original scanned pixels, plus
 *   the new text drawn visibly on top.
 *
 * Never touches PdfTextReplacementEngine — scanned pages have no text objects.
 * The original file is never modified. Single-thread IO dispatcher throughout.
 */
@Singleton
class OcrEditExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val layerWriter: SearchableLayerWriter,
    private val fontProvider: OcrFontProvider
) {

    companion object {
        private const val TAG = "OcrEditExporter"

        /** Render width for background sampling — modest, only colors are needed. */
        private const val SAMPLE_RENDER_WIDTH = 1200
    }

    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun export(
        inputPath: String,
        pages: List<OcrPage>,
        edits: List<OcrWordEdit>,
        outputPath: String
    ): OcrExportResult? = withContext(pdfDispatcher) {
        val input = File(inputPath)
        if (!input.exists() || input.length() == 0L) return@withContext null

        val editsByPage = edits.groupBy { it.pageIndex }
        var renderer: PdfPageRenderer? = null

        try {
            PDDocument.load(input).use { doc ->
                var editsApplied = 0
                var invisibleWords = 0

                for (page in pages.sortedBy { it.pageIndex }) {
                    if (page.pageIndex >= doc.numberOfPages) continue
                    coroutineContext.ensureActive()

                    val pdPage = doc.getPage(page.pageIndex)
                    val pageEdits = editsByPage[page.pageIndex].orEmpty()

                    invisibleWords += layerWriter.writeInvisibleWords(
                        doc, pdPage, page,
                        exclude = pageEdits.map { it.originalWord }.toSet()
                    )
                    if (pageEdits.isEmpty()) continue

                    // Render once per edited page to sample background colors.
                    if (renderer == null) renderer = PdfPageRenderer(context, inputPath, null)
                    val bitmap = renderer?.renderPage(page.pageIndex, SAMPLE_RENDER_WIDTH, false)
                    val patchColors = pageEdits.associate { edit ->
                        edit.id to (bitmap?.let { BackgroundColorSampler.sample(it, edit.originalWord) }
                            ?: Color.WHITE)
                    }
                    bitmap?.recycle()

                    val cropBox = pdPage.cropBox
                    PDPageContentStream(
                        doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        for (edit in pageEdits) {
                            val word = edit.originalWord
                            val rect = OcrCoordinateMapper.toPdfRect(
                                rect = OcrCoordinateMapper.NormalizedRect(word.x, word.y, word.width, word.height),
                                cropWidth = cropBox.width,
                                cropHeight = cropBox.height,
                                cropOffsetX = cropBox.lowerLeftX,
                                cropOffsetY = cropBox.lowerLeftY,
                                rotation = page.pageRotation
                            )
                            val anchor = OcrCoordinateMapper.toPdfAnchor(
                                rect = OcrCoordinateMapper.NormalizedRect(word.x, word.y, word.width, word.height),
                                cropWidth = cropBox.width,
                                cropHeight = cropBox.height,
                                cropOffsetX = cropBox.lowerLeftX,
                                cropOffsetY = cropBox.lowerLeftY,
                                rotation = page.pageRotation
                            )

                            // 1. Patch: cover the original scanned word. Proportional
                            //    inflation so the original glyph's halo / ascenders /
                            //    descenders don't survive around the fill (the overlap bug).
                            val patch = patchColors[edit.id] ?: Color.WHITE
                            val patchRect = OcrPatchGeometry.inflate(rect)
                            cs.setNonStrokingColor(Color.red(patch), Color.green(patch), Color.blue(patch))
                            cs.addRect(patchRect.x, patchRect.y, patchRect.width, patchRect.height)
                            cs.fill()

                            // 2. Visible replacement text.
                            val font = fontProvider.fontFor(doc, page.script, edit.newText)
                                ?: PDType1Font.HELVETICA
                            val textColor = edit.color
                            cs.beginText()
                            cs.setRenderingMode(RenderingMode.FILL)
                            cs.setNonStrokingColor(
                                (textColor.red * 255).toInt(),
                                (textColor.green * 255).toInt(),
                                (textColor.blue * 255).toInt()
                            )
                            cs.setFont(font, edit.fontSizePdf.coerceIn(1f, 300f))
                            if (anchor.rotationDegrees != 0) {
                                cs.setTextMatrix(
                                    Matrix.getRotateInstance(
                                        Math.toRadians(anchor.rotationDegrees.toDouble()),
                                        anchor.x, anchor.y
                                    )
                                )
                            } else {
                                cs.newLineAtOffset(anchor.x, anchor.y)
                            }
                            showSafely(cs, edit.newText)
                            cs.endText()
                            editsApplied++
                        }
                    }
                }

                doc.save(File(outputPath))
                OcrExportResult(outputPath, editsApplied, invisibleWords)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e(TAG, "OCR edit export failed for $inputPath", e)
            null
        } finally {
            renderer?.close()
        }
    }

    /** The chosen font should encode the text; sanitize as a last resort so showText never throws. */
    private fun showSafely(cs: PDPageContentStream, text: String) {
        try {
            cs.showText(text)
        } catch (_: Exception) {
            try { cs.showText(text.replace(Regex("[^\\x20-\\x7E]"), "?")) } catch (_: Exception) {}
        }
    }
}
