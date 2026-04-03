package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Thread-safe PDF page renderer wrapper.
 * All render calls must be serialized by the caller (pdfDispatcher).
 */
class PdfPageRenderer(
    private val context: Context,
    private val filePath: String,
    private val password: String? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "PdfPageRenderer"
    }

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

    private val screenWidth: Int
    private val screenHeight: Int

    /** Returns true if the renderer is initialized and ready to render. */
    val isReady: Boolean
        get() = renderer != null && pageCount > 0

    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        openRenderer()
    }

    private fun openRenderer() {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return
            }
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd?.let { fileDescriptor ->
                renderer = if (Build.VERSION.SDK_INT >= 35 && password != null) {
                    try {
                        val loadParamsClass = Class.forName("android.graphics.pdf.LoadParams")
                        val builderClass = Class.forName("android.graphics.pdf.LoadParams\$Builder")
                        val builder = builderClass.getDeclaredConstructor().newInstance()
                        builderClass.getMethod("setPassword", String::class.java).invoke(builder, password)
                        val loadParams = builderClass.getMethod("build").invoke(builder)
                        PdfRenderer::class.java.getConstructor(ParcelFileDescriptor::class.java, loadParamsClass)
                            .newInstance(fileDescriptor, loadParams) as PdfRenderer
                    } catch (e: Exception) {
                        Log.w(TAG, "Password-based open failed, falling back", e)
                        PdfRenderer(fileDescriptor)
                    }
                } else {
                    PdfRenderer(fileDescriptor)
                }
                pageCount = renderer?.pageCount ?: 0
                Log.d(TAG, "Renderer opened: pageCount=$pageCount, file=$filePath")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening PDF", e)
            throw e // Propagate for password handling
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PDF renderer", e)
            if (e.message?.contains("password", ignoreCase = true) == true) throw e
        }
    }

    /**
     * Reinitialize the renderer (e.g., after repeated failures).
     * Closes old resources and reopens.
     */
    fun reinitialize() {
        Log.w(TAG, "Reinitializing renderer for: $filePath")
        try {
            renderer?.close()
            pfd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing old renderer during reinitialize", e)
        }
        renderer = null
        pfd = null
        pageCount = 0
        openRenderer()
    }

    fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        if (!isReady || pageIndex !in 0 until pageCount) return null
        return try {
            renderer!!.openPage(pageIndex).let { page ->
                val dims = Pair(page.width, page.height)
                page.close()
                dims
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPageDimensions failed for page $pageIndex", e)
            null
        }
    }

    /** Standard rendering: width specified, height auto-calculated. */
    fun renderPage(pageIndex: Int, width: Int, isInverted: Boolean = false): Bitmap? {
        if (!isReady) {
            Log.e(TAG, "renderPage called but renderer not ready (renderer=${renderer != null}, pageCount=$pageCount)")
            return null
        }
        if (pageIndex !in 0 until pageCount) {
            Log.e(TAG, "Invalid page index: $pageIndex (pageCount=$pageCount)")
            return null
        }
        if (width <= 0) {
            Log.e(TAG, "Invalid width: $width")
            return null
        }

        return try {
            val page = renderer!!.openPage(pageIndex)
            try {
                val ratio = page.height.toFloat() / page.width.toFloat()
                val scaledWidth = min(width, screenWidth * 2)
                val height = (scaledWidth * ratio).roundToInt().coerceAtLeast(1)
                renderBitmap(page, scaledWidth, height, isInverted)
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "renderPage FAILED for page $pageIndex, width=$width", e)
            null
        }
    }

    /** Multi-resolution rendering for zoom. */
    fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int, isInverted: Boolean = false): Bitmap? {
        if (!isReady) {
            Log.e(TAG, "renderPage(zoom) called but renderer not ready")
            return null
        }
        if (pageIndex !in 0 until pageCount) return null
        val w = min(targetWidth, screenWidth * 4).coerceAtLeast(1)
        val h = min(targetHeight, screenHeight * 4).coerceAtLeast(1)

        return try {
            val page = renderer!!.openPage(pageIndex)
            try {
                renderBitmap(page, w, h, isInverted)
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "renderPage(zoom) FAILED for page $pageIndex", e)
            null
        }
    }

    private fun renderBitmap(page: PdfRenderer.Page, width: Int, height: Int, isInverted: Boolean): Bitmap {
        // MUST use ARGB_8888 — Android 15+ PdfRenderer rejects RGB_565 ("Unsupported pixel format")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (isInverted) Color.BLACK else Color.WHITE)

        if (isInverted) {
            val paint = android.graphics.Paint()
            val matrix = android.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            tempBitmap.eraseColor(Color.WHITE)
            page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            canvas.drawBitmap(tempBitmap, 0f, 0f, paint)
            tempBitmap.recycle()
        } else {
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        return bitmap
    }

    override fun close() {
        try {
            renderer?.close()
            pfd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing renderer", e)
        }
        renderer = null
        pfd = null
    }
}
