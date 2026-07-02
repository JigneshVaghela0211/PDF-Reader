package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWord

/**
 * Estimates the page background color around a word so the export patch blends
 * into the scan. Samples a ring of pixels just outside the word's bounding box
 * and takes the per-channel **median** — robust against noise and against the
 * glyph pixels themselves. Falls back to white.
 */
object BackgroundColorSampler {

    private const val RING_OFFSET_PX = 3
    private const val SAMPLE_STEP_PX = 4

    /** Returns packed ARGB (alpha always 0xFF). */
    fun sample(bitmap: Bitmap, word: OcrWord): Int {
        val left = (word.x * bitmap.width).toInt() - RING_OFFSET_PX
        val top = (word.y * bitmap.height).toInt() - RING_OFFSET_PX
        val right = ((word.x + word.width) * bitmap.width).toInt() + RING_OFFSET_PX
        val bottom = ((word.y + word.height) * bitmap.height).toInt() + RING_OFFSET_PX

        val reds = ArrayList<Int>(64)
        val greens = ArrayList<Int>(64)
        val blues = ArrayList<Int>(64)

        fun sampleAt(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return
            val pixel = bitmap.getPixel(x, y)
            reds.add(Color.red(pixel))
            greens.add(Color.green(pixel))
            blues.add(Color.blue(pixel))
        }

        var x = left
        while (x <= right) {
            sampleAt(x, top)
            sampleAt(x, bottom)
            x += SAMPLE_STEP_PX
        }
        var y = top
        while (y <= bottom) {
            sampleAt(left, y)
            sampleAt(right, y)
            y += SAMPLE_STEP_PX
        }

        if (reds.isEmpty()) return Color.WHITE
        return Color.rgb(median(reds), median(greens), median(blues))
    }

    private fun median(values: ArrayList<Int>): Int {
        values.sort()
        return values[values.size / 2]
    }
}
