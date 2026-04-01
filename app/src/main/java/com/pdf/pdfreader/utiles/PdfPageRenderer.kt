package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

class PdfPageRenderer(
    private val context: Context, 
    private val filePath: String,
    private val password: String? = null
) : AutoCloseable {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

    init {
        val file = File(filePath)
        if (file.exists()) {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd?.let { fileDescriptor ->
                renderer = if (Build.VERSION.SDK_INT >= 35 && password != null) { 
                    try {
                        // Using Reflection to support Android 15 password feature even if 
                        // local SDK 35 is not fully recognized by the IDE/Compiler.
                        // This mirrors: PdfRenderer(fileDescriptor, LoadParams.Builder().setPassword(password).build())
                        
                        val loadParamsClass = Class.forName("android.graphics.pdf.LoadParams")
                        val builderClass = Class.forName("android.graphics.pdf.LoadParams\$Builder")
                        
                        val builder = builderClass.getDeclaredConstructor().newInstance()
                        val setPasswordMethod = builderClass.getMethod("setPassword", String::class.java)
                        setPasswordMethod.invoke(builder, password)
                        
                        val buildMethod = builderClass.getMethod("build")
                        val loadParams = buildMethod.invoke(builder)
                        
                        val pdfRendererConstructor = PdfRenderer::class.java.getConstructor(
                            ParcelFileDescriptor::class.java, 
                            loadParamsClass
                        )
                        pdfRendererConstructor.newInstance(fileDescriptor, loadParams) as PdfRenderer
                    } catch (e: Exception) {
                        PdfRenderer(fileDescriptor)
                    }
                } else {
                    PdfRenderer(fileDescriptor)
                }
                pageCount = renderer?.pageCount ?: 0
            }
        }
    }

    fun renderPage(pageIndex: Int, width: Int, isInverted: Boolean = false): Bitmap? {
        if (pageIndex !in 0 until pageCount) return null
        
        return try {
            val page = renderer?.openPage(pageIndex)
            page?.let { p ->
                val ratio = p.height.toFloat() / p.width.toFloat()
                val height = (width * ratio).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(if (isInverted) Color.BLACK else Color.WHITE)
                
                if (isInverted) {
                    val paint = android.graphics.Paint()
                    val matrix = android.graphics.ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                    
                    // Render to a temporary bitmap first to apply filter
                    val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    p.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    canvas.drawBitmap(tempBitmap, 0f, 0f, paint)
                    tempBitmap.recycle()
                } else {
                    p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                
                p.close()
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        try {
            renderer?.close()
            pfd?.close()
        } catch (e: Exception) {
            // Log or ignore
        }
    }
}
