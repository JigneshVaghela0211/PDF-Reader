package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF

/**
 * Lightweight OCR pre-processing without OpenCV: grayscale + mild contrast boost
 * (ML Kit copes well with moderate noise/skew on its own), plus an upscale for
 * pages rendered below a useful OCR resolution.
 *
 * Deskew is intentionally NOT done here — the recognizer reports a per-line
 * `angle` instead, which the UI can use to warn about heavily skewed scans.
 *
 * Returns the input bitmap unchanged when there is nothing to do; otherwise the
 * caller owns both bitmaps and must recycle the input separately.
 */
object OcrImagePreProcessor {

    /** Below this width (px) OCR accuracy drops noticeably; upscale to [TARGET_WIDTH]. */
    private const val MIN_WIDTH = 1200
    private const val TARGET_WIDTH = 1654

    private const val CONTRAST = 1.3f

    fun preprocess(input: Bitmap): Bitmap {
        val scale = if (input.width < MIN_WIDTH) TARGET_WIDTH / input.width.toFloat() else 1f
        val outWidth = (input.width * scale).toInt()
        val outHeight = (input.height * scale).toInt()

        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val offset = 128f * (1f - CONTRAST)
        colorMatrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    CONTRAST, 0f, 0f, 0f, offset,
                    0f, CONTRAST, 0f, 0f, offset,
                    0f, 0f, CONTRAST, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        Canvas(output).drawBitmap(
            input,
            null,
            RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat()),
            paint
        )
        return output
    }
}
