package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val renderSemaphore = Semaphore(3) // Limit to 3 concurrent renders
    private val activeLoads = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    
    // Memory Cache
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val thumbnailDir = File(context.cacheDir, "pdf_thumbnails").apply {
        if (!exists()) mkdirs()
    }

    fun getThumbnailFile(path: String): File {
        val cacheKey = path.hashCode().toString()
        return File(thumbnailDir, "$cacheKey.jpg")
    }

    suspend fun getThumbnail(path: String, isLocked: Boolean): Bitmap? {
        if (isLocked) return null

        val cacheKey = path.hashCode().toString()
        
        // 1. Fast Memory Cache Check
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Request Coalescing: Join existing load or start new one
        val deferred = activeLoads.getOrPut(cacheKey) {
            scope.async {
                loadAndCacheThumbnail(path, cacheKey)
            }
        }

        return try {
            deferred.await()
        } catch (e: Exception) {
            activeLoads.remove(cacheKey)
            null
        }
    }

    private suspend fun loadAndCacheThumbnail(path: String, cacheKey: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check Disk Cache again inside deferred
            val thumbnailFile = File(thumbnailDir, "$cacheKey.jpg")
            if (thumbnailFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            }

            // 3. Generate Thumbnail with rendering limit
            renderSemaphore.withPermit {
                generateAndSaveThumbnail(path, cacheKey)
            }
        } finally {
            // Ensure we remove the job from active loads
            activeLoads.remove(cacheKey)
        }
    }

    private fun generateAndSaveThumbnail(path: String, cacheKey: String): Bitmap? {
        try {
            val file = File(path)
            if (!file.exists()) return null
            
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
                
                // Fill with white background (PDF pages are transparent by default)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Save to Disk for future sessions
                saveToDisk(cacheKey, bitmap)
                
                // Save to Memory for current session
                memoryCache.put(cacheKey, bitmap)
                
                page.close()
                renderer.close()
                pfd.close()
                return bitmap
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun saveToDisk(key: String, bitmap: Bitmap) {
        try {
            val file = File(thumbnailDir, "$key.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
