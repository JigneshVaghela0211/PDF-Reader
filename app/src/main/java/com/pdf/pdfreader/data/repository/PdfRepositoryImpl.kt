package com.pdf.pdfreader.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.domain.repository.PdfRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.pdf.pdfreader.data.local.PdfDao
import com.pdf.pdfreader.data.local.PdfEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.pdf.pdfreader.utiles.ThumbnailManager
import kotlin.math.log10

@Singleton
class PdfRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfDao: PdfDao,
    private val thumbnailManager: ThumbnailManager
) : PdfRepository {
    
    override fun getPdfFiles(): Flow<List<PdfFile>> = pdfDao.getAllPdfs().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun refreshPdfFiles() {
        withContext(Dispatchers.IO) {
            val files = mutableListOf<PdfEntity>()
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf")
            val projection = mutableListOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.Files.FileColumns.IS_TRASHED)
                }
            }.toTypedArray()

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idPath = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val idName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val idSize = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val idDate = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val idType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                val idTrashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
                } else -1

                while (cursor.moveToNext()) {
                    val path = cursor.getString(idPath)
                    val name = cursor.getString(idName)
                    val size = cursor.getLong(idSize)
                    val date = cursor.getLong(idDate) * 1000
                    val type = cursor.getString(idType)
                    val isTrashed = if (idTrashed != -1) cursor.getInt(idTrashed) == 1 else false
                    
                    val isLocked = isPdfLocked(path)
                    val thumbnailFile = thumbnailManager.getThumbnailFile(path)
                    val thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null

                    files.add(
                        PdfEntity(
                            path = path,
                            name = name,
                            size = size,
                            lastModified = date,
                            type = type,
                            formattedSize = formatFileSize(size),
                            formattedDate = formatDate(date),
                            isLocked = isLocked,
                            isTrashed = isTrashed,
                            thumbnailPath = thumbnailPath
                        )
                    )
                }
            }
            pdfDao.syncPdfs(files)
        }
    }

    private fun PdfEntity.toDomain() = PdfFile(
        path = path,
        name = name,
        lastModified = lastModified,
        size = size,
        type = type,
        formattedSize = formattedSize,
        formattedDate = formattedDate,
        isLocked = isLocked,
        isTrashed = isTrashed,
        thumbnailPath = thumbnailPath
    )

    private fun isPdfLocked(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfRenderer(pfd).use { }
                false
            } catch (e: SecurityException) {
                true
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
