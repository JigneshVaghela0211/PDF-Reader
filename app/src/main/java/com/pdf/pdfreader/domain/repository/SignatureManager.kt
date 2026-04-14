package com.pdf.pdfreader.domain.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.toColorInt
import com.pdf.pdfreader.ui.components.SignatureStroke
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val signaturesDir = File(context.filesDir, "signatures").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Renders a list of strokes to a transparent PNG and saves it to internal storage.
     * Returns the absolute file URI.
     */
    suspend fun saveSignature(strokes: List<SignatureStroke>, width: Float, height: Float): String = withContext(Dispatchers.IO) {
        // Enforce max bounds and padding
        val bW = width.toInt().coerceAtLeast(1)
        val bH = height.toInt().coerceAtLeast(1)
        
        val bitmap = Bitmap.createBitmap(bW, bH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        for (stroke in strokes) {
            paint.color = android.graphics.Color.argb(
                (stroke.color.alpha * 255).toInt(),
                (stroke.color.red * 255).toInt(),
                (stroke.color.green * 255).toInt(),
                (stroke.color.blue * 255).toInt()
            )
            paint.strokeWidth = stroke.strokeWidth

            if (stroke.points.size >= 2) {
                val path = Path()
                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(path, paint)
            } else if (stroke.points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
            }
        }

        // Crop transparent edges
        val cropped = cropTransparent(bitmap)
        if (bitmap != cropped) bitmap.recycle()

        val fileName = "sig_${System.currentTimeMillis()}.png"
        val file = File(signaturesDir, fileName)

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        cropped.recycle()

        "file://${file.absolutePath}"
    }

    suspend fun getAllSignatureUris(): List<String> = withContext(Dispatchers.IO) {
        signaturesDir.listFiles()?.filter { it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { "file://${it.absolutePath}" }
            ?: emptyList()
    }

    suspend fun deleteSignature(uri: String): Boolean = withContext(Dispatchers.IO) {
        val path = uri.replace("file://", "")
        val file = File(path)
        if (file.exists() && file.parentFile?.absolutePath == signaturesDir.absolutePath) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Re-renders signature strokes with updated properties (thickness/color)
     * to a NEW file. Returns the new URI. The old file is NOT deleted
     * (it may be referenced by undo history).
     */
    suspend fun reRenderSignature(
        strokes: List<SignatureStroke>,
        width: Float,
        height: Float
    ): String = withContext(Dispatchers.IO) {
        val bW = width.toInt().coerceAtLeast(1)
        val bH = height.toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bW, bH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        for (stroke in strokes) {
            paint.color = android.graphics.Color.argb(
                (stroke.color.alpha * 255).toInt(),
                (stroke.color.red * 255).toInt(),
                (stroke.color.green * 255).toInt(),
                (stroke.color.blue * 255).toInt()
            )
            paint.strokeWidth = stroke.strokeWidth

            if (stroke.points.size >= 2) {
                val path = Path()
                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(path, paint)
            } else if (stroke.points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
            }
        }

        val cropped = cropTransparent(bitmap)
        if (bitmap != cropped) bitmap.recycle()

        val fileName = "sig_${System.currentTimeMillis()}.png"
        val file = File(signaturesDir, fileName)

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        cropped.recycle()

        "file://${file.absolutePath}"
    }

    private fun cropTransparent(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (pixels[y * width + x] shr 24) and 0xff
                if (alpha > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // If completely transparent, return original
        if (maxX < minX || maxY < minY) return bitmap

        // Add 8px padding
        minX = (minX - 8).coerceAtLeast(0)
        minY = (minY - 8).coerceAtLeast(0)
        maxX = (maxX + 8).coerceAtMost(width - 1)
        maxY = (maxY + 8).coerceAtMost(height - 1)

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        return Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
    }
}
