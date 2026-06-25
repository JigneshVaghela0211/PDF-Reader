package com.pdf.pdfreader.utiles

import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Reduces PDF file size by re-encoding oversized raster images — the dominant source of bloat in
 * photo/scan-heavy PDFs — at a lower resolution and JPEG quality, then re-saving (which also
 * compacts and FLATE-compresses the content streams).
 *
 * Conservative by design:
 *  - Only images whose largest side exceeds the level's target dimension are touched, so small
 *    icons/logos are never enlarged by a needless re-encode.
 *  - Images with a soft mask / stencil (transparency) are skipped, because JPEG has no alpha and
 *    re-encoding would corrupt them.
 *  - If a re-encode somehow comes out larger, the original image is kept.
 *
 * Output is a sibling `*_compressed.pdf`; the original is never modified. Serialized on one IO
 * thread (PDFBox is not concurrency-safe here).
 */
@Singleton
class PdfCompressionEngine @Inject constructor() {

    companion object {
        private const val TAG = "PdfCompressionEngine"
    }

    /** Compression strength. Higher level ⇒ smaller file ⇒ lower image fidelity. */
    enum class Level(val maxDimension: Int, val jpegQuality: Float) {
        LOW(1600, 0.80f),
        MEDIUM(1200, 0.60f),
        HIGH(900, 0.40f)
    }

    data class CompressResult(
        val outputPath: String,
        val originalBytes: Long,
        val compressedBytes: Long,
        val imagesProcessed: Int
    ) {
        /** Fraction saved, 0..1 (0 if it didn't get smaller). */
        val savedFraction: Float
            get() = if (originalBytes > 0 && compressedBytes < originalBytes)
                (originalBytes - compressedBytes).toFloat() / originalBytes else 0f
    }

    suspend fun compress(
        inputPath: String,
        level: Level,
        outputPath: String? = null
    ): CompressResult? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val input = File(inputPath)
        if (!input.exists() || input.length() == 0L) return@withContext null
        val out = outputPath ?: defaultOutput(input)
        val originalBytes = input.length()
        var processed = 0

        try {
            PDDocument.load(input).use { doc ->
                for (pageIndex in 0 until doc.numberOfPages) {
                    coroutineContext.ensureActive()
                    val resources = doc.getPage(pageIndex).resources ?: continue
                    for (name in resources.xObjectNames) {
                        if (!resources.isImageXObject(name)) continue
                        val xobject = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xobject !is PDImageXObject) continue
                        if (recompressImage(doc, resources, name, xobject, level)) processed++
                    }
                }
                doc.save(File(out))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            return@withContext null
        }

        val result = CompressResult(out, originalBytes, File(out).length(), processed)
        Log.d(TAG, "Compressed $inputPath: ${originalBytes}→${result.compressedBytes} bytes, $processed images")
        result
    }

    /**
     * Re-encode one image XObject in place if it is oversized and opaque. Returns true if it was
     * replaced.
     */
    private fun recompressImage(
        doc: PDDocument,
        resources: com.tom_roush.pdfbox.pdmodel.PDResources,
        name: com.tom_roush.pdfbox.cos.COSName,
        image: PDImageXObject,
        level: Level
    ): Boolean {
        return try {
            // Transparency can't survive a JPEG round-trip — leave those alone.
            if (image.isStencil || image.softMask != null) return false

            val w = image.width
            val h = image.height
            val longest = maxOf(w, h)
            if (longest <= level.maxDimension) return false // already small enough

            val original = image.image ?: return false
            val scale = level.maxDimension.toFloat() / longest
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)

            val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)
            try {
                val replacement = JPEGFactory.createFromImage(doc, scaled, level.jpegQuality)
                resources.put(name, replacement)
                true
            } finally {
                if (scaled != original) scaled.recycle()
                original.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skipping image '$name' (re-encode failed)", e)
            false
        }
    }

    private fun defaultOutput(input: File): String {
        val parent = input.parentFile?.absolutePath ?: input.absolutePath
        return "$parent/${input.nameWithoutExtension}_compressed.pdf"
    }
}
