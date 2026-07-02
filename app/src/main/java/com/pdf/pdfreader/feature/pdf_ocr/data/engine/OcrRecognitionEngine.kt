package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrBlock
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrLine
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrProgress
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrRecognitionEvent
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWord
import com.pdf.pdfreader.utiles.PdfPageRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * The OCR recognition core: renders a page, pre-processes the bitmap, runs the
 * ML Kit recognizer for the chosen script and maps the result into the
 * [OcrPage] hierarchy (normalized display coords + confidence + angle).
 *
 * Serialized on a single IO thread (PdfPageRenderer + PDFBox are not
 * concurrency-safe); one renderer + one recognizer are reused across a whole
 * document run. Per-page failures/timeouts skip the page instead of failing
 * the run.
 */
@Singleton
class OcrRecognitionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OcrRecognitionEngine"

        /** Render width in px (~200 DPI for A4) — accuracy/perf trade-off for OCR. */
        const val DEFAULT_RENDER_WIDTH = 1654

        /** Reduced width for a one-shot retry after an OutOfMemoryError. */
        private const val FALLBACK_RENDER_WIDTH = 1200

        private const val PAGE_TIMEOUT_MS = 30_000L
    }

    private val ocrDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Recognize a single page. Throws on unrecoverable errors (missing file/page). */
    suspend fun recognizePage(
        inputPath: String,
        pageIndex: Int,
        script: OcrScript,
        renderWidth: Int = DEFAULT_RENDER_WIDTH
    ): OcrPage = withContext(ocrDispatcher) {
        val input = File(inputPath)
        check(input.exists() && input.length() > 0L) { "PDF not found: $inputPath" }

        val recognizer = TextRecognizerFactory.create(script)
        val renderer = PdfPageRenderer(context, inputPath, null)
        try {
            PDDocument.load(input).use { doc ->
                recognizePageWithOomRetry(doc, renderer, recognizer, pageIndex, script, renderWidth)
                    ?: error("Page $pageIndex could not be rendered")
            }
        } finally {
            renderer.close()
            recognizer.close()
        }
    }

    /**
     * Recognize a whole document, emitting one [OcrRecognitionEvent] per page.
     * [skipPages] (already cached) are counted in progress with `fromCache = true`
     * but not re-recognized. Failed pages emit progress with a null page.
     */
    fun recognizeDocument(
        inputPath: String,
        script: OcrScript,
        skipPages: Set<Int> = emptySet(),
        renderWidth: Int = DEFAULT_RENDER_WIDTH
    ): Flow<OcrRecognitionEvent> = flow {
        val input = File(inputPath)
        check(input.exists() && input.length() > 0L) { "PDF not found: $inputPath" }

        val recognizer = TextRecognizerFactory.create(script)
        val renderer = PdfPageRenderer(context, inputPath, null)
        try {
            PDDocument.load(input).use { doc ->
                val pageCount = minOf(doc.numberOfPages, renderer.pageCount)
                var wordsSoFar = 0
                for (i in 0 until pageCount) {
                    currentCoroutineContext().ensureActive()

                    val fromCache = i in skipPages
                    val page = if (fromCache) null else try {
                        recognizePageWithOomRetry(doc, renderer, recognizer, i, script, renderWidth)
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        Log.w(TAG, "OCR failed on page $i; skipping", e)
                        null
                    }
                    if (page != null) wordsSoFar += page.wordCount

                    emit(
                        OcrRecognitionEvent(
                            progress = OcrProgress(
                                currentPage = i + 1,
                                totalPages = pageCount,
                                fraction = (i + 1) / pageCount.toFloat(),
                                wordsSoFar = wordsSoFar,
                                fromCache = fromCache
                            ),
                            page = page
                        )
                    )
                }
            }
        } finally {
            renderer.close()
            recognizer.close()
        }
    }.flowOn(ocrDispatcher)

    /** Memory-pressure defense: one retry at a reduced render width after an OOM. */
    private suspend fun recognizePageWithOomRetry(
        doc: PDDocument,
        renderer: PdfPageRenderer,
        recognizer: TextRecognizer,
        pageIndex: Int,
        script: OcrScript,
        renderWidth: Int
    ): OcrPage? = try {
        recognizePageInternal(doc, renderer, recognizer, pageIndex, script, renderWidth)
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "OOM on page $pageIndex at ${renderWidth}px — retrying at ${FALLBACK_RENDER_WIDTH}px")
        System.gc()
        recognizePageInternal(doc, renderer, recognizer, pageIndex, script, FALLBACK_RENDER_WIDTH)
    }

    private suspend fun recognizePageInternal(
        doc: PDDocument,
        renderer: PdfPageRenderer,
        recognizer: TextRecognizer,
        pageIndex: Int,
        script: OcrScript,
        renderWidth: Int
    ): OcrPage? {
        val bitmap = renderer.renderPage(pageIndex, renderWidth, false) ?: return null
        var processed: Bitmap = bitmap
        try {
            processed = OcrImagePreProcessor.preprocess(bitmap)
            coroutineContext.ensureActive()

            val text = Tasks.await(
                recognizer.process(InputImage.fromBitmap(processed, 0)),
                PAGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )

            val page = doc.getPage(pageIndex)
            return buildOcrPage(
                text = text,
                pageIndex = pageIndex,
                script = script,
                cropWidth = page.cropBox.width,
                cropHeight = page.cropBox.height,
                pageRotation = page.rotation,
                bitmapWidth = processed.width,
                bitmapHeight = processed.height
            )
        } finally {
            processed.recycle()
            if (processed !== bitmap) bitmap.recycle()
        }
    }

    private fun buildOcrPage(
        text: Text,
        pageIndex: Int,
        script: OcrScript,
        cropWidth: Float,
        cropHeight: Float,
        pageRotation: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): OcrPage {
        val blocks = text.textBlocks.mapNotNull { block ->
            val lines = block.lines.mapNotNull { line ->
                val words = line.elements.mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    if (element.text.isBlank()) return@mapNotNull null
                    val rect = OcrCoordinateMapper.toNormalized(
                        box.left, box.top, box.right, box.bottom, bitmapWidth, bitmapHeight
                    )
                    OcrWord(
                        text = element.text,
                        x = rect.x, y = rect.y, width = rect.width, height = rect.height,
                        confidence = element.confidence ?: 1f,
                        angle = element.angle ?: 0f
                    )
                }
                if (words.isEmpty()) return@mapNotNull null
                val bounds = line.boundingBox?.let {
                    OcrCoordinateMapper.toNormalized(it.left, it.top, it.right, it.bottom, bitmapWidth, bitmapHeight)
                } ?: union(words)
                OcrLine(
                    text = line.text,
                    x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height,
                    confidence = line.confidence ?: 1f,
                    angle = line.angle ?: 0f,
                    words = words
                )
            }
            if (lines.isEmpty()) return@mapNotNull null
            val bounds = block.boundingBox?.let {
                OcrCoordinateMapper.toNormalized(it.left, it.top, it.right, it.bottom, bitmapWidth, bitmapHeight)
            } ?: union(lines.flatMap { it.words })
            OcrBlock(
                text = block.text,
                x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height,
                lines = lines
            )
        }

        return OcrPage(
            pageIndex = pageIndex,
            script = script,
            pdfPageWidth = cropWidth,
            pdfPageHeight = cropHeight,
            pageRotation = pageRotation,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            blocks = blocks,
            createdAtMs = System.currentTimeMillis()
        )
    }

    private fun union(words: List<OcrWord>): OcrCoordinateMapper.NormalizedRect {
        val left = words.minOf { it.x }
        val top = words.minOf { it.y }
        val right = words.maxOf { it.x + it.width }
        val bottom = words.maxOf { it.y + it.height }
        return OcrCoordinateMapper.NormalizedRect(left, top, right - left, bottom - top)
    }
}
