package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfPageRenderer(private val context: Context, private val filePath: String) : AutoCloseable {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

    init {
        val file = File(filePath)
        if (file.exists()) {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd?.let {
                renderer = PdfRenderer(it)
                pageCount = renderer?.pageCount ?: 0
            }
        }
    }

    fun renderPage(pageIndex: Int, width: Int): Bitmap? {
        if (pageIndex !in 0..pageCount) return null
        
        return try {
            val page = renderer?.openPage(pageIndex)
            page?.let { p ->
                val ratio = p.height.toFloat() / p.width.toFloat()
                val height = (width * ratio).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                p.close()
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        renderer?.close()
        pfd?.close()
    }
}
