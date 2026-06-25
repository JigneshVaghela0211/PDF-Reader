package com.pdf.pdfreader.utiles

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Turns a scanned (image-only) PDF into a searchable one by adding an **invisible** text layer.
 *
 * Pipeline per page:
 *   render page → bitmap  →  ML Kit on-device OCR  →  draw recognized words with text-rendering
 *   mode NEITHER (Tr 3, invisible) at their mapped positions  →  save.
 *
 * The original raster stays visible and pixel-identical; the hidden glyphs make the text
 * selectable / searchable / copyable in any viewer. Output is a sibling `*_ocr.pdf`.
 *
 * Limitations (BETA): tuned for Latin script and pages without a /Rotate entry. Serialized on a
 * single IO thread (PdfRenderer + PDFBox are not concurrency-safe here).
 */
@Singleton
class PdfOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PdfOcrEngine"
        /** Render width in px (~200 DPI for A4) — a good accuracy/perf trade-off for OCR. */
        private const val DEFAULT_RENDER_WIDTH = 1654
    }

    data class OcrResult(
        val outputPath: String,
        val pagesProcessed: Int,
        val wordsAdded: Int
    )

    suspend fun makeSearchable(
        inputPath: String,
        renderWidth: Int = DEFAULT_RENDER_WIDTH,
        outputPath: String? = null
    ): OcrResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val input = File(inputPath)
        if (!input.exists() || input.length() == 0L) return@withContext null
        val out = outputPath ?: defaultOutput(input)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val renderer = PdfPageRenderer(context, inputPath, null)
        var pagesProcessed = 0
        var wordsAdded = 0

        try {
            PDDocument.load(input).use { doc ->
                val pageCount = minOf(doc.numberOfPages, renderer.pageCount)
                for (i in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    val bitmap = renderer.renderPage(i, renderWidth, false) ?: continue
                    try {
                        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                        val elements = text.textBlocks
                            .flatMap { it.lines }
                            .flatMap { it.elements }
                            .filter { !it.text.isNullOrBlank() && it.boundingBox != null }
                        if (elements.isEmpty()) continue

                        val page = doc.getPage(i)
                        val cropBox = page.cropBox
                        val scaleX = cropBox.width / bitmap.width
                        val scaleY = cropBox.height / bitmap.height

                        PDPageContentStream(
                            doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { cs ->
                            for (element in elements) {
                                val box = element.boundingBox ?: continue
                                val fontSize = (box.height() * scaleY).coerceIn(1f, 300f)
                                val x = box.left * scaleX
                                // Bitmap origin is top-left; PDF is bottom-left. Use the box bottom as baseline.
                                val baselineY = cropBox.height - box.bottom * scaleY
                                cs.beginText()
                                cs.setRenderingMode(RenderingMode.NEITHER) // invisible
                                cs.setFont(PDType1Font.HELVETICA, fontSize)
                                cs.newLineAtOffset(x, baselineY)
                                showSafely(cs, element.text)
                                cs.endText()
                                wordsAdded++
                            }
                        }
                        pagesProcessed++
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR failed on page $i; leaving it unchanged", e)
                    } finally {
                        bitmap.recycle()
                    }
                }
                doc.save(File(out))
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR pipeline failed", e)
            return@withContext null
        } finally {
            renderer.close()
            recognizer.close()
        }

        Log.d(TAG, "OCR done: $pagesProcessed pages, $wordsAdded words → $out")
        OcrResult(out, pagesProcessed, wordsAdded)
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
