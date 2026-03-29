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
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

@Singleton
class PdfRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfDao: PdfDao,
    private val thumbnailManager: ThumbnailManager,
    private val textExtractor: com.pdf.pdfreader.utiles.PdfTextExtractor
) : PdfRepository {
    
    override fun getPdfFiles(): Flow<List<PdfFile>> = pdfDao.getAllPdfs().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getFavoritePdfs(): Flow<List<PdfFile>> = pdfDao.getFavoritePdfs().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getRecentPdfs(): Flow<List<PdfFile>> = pdfDao.getRecentPdfs().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun syncFilesWithStorage() {
        withContext(Dispatchers.IO) {
            // Get current DB state to preserve user metadata (favorites, lastOpened)
            val existingPdfs = pdfDao.getAllPdfsOnce().associateBy { it.path }
            val scannedFiles = mutableListOf<PdfEntity>()
            val scannedPaths = mutableListOf<String>()
            
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

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
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
                    val date = cursor.getLong(idDate) * 1000
                    scannedPaths.add(path)
                    
                    val dbEntity = existingPdfs[path]
                    
                    // If file not in DB, scan it. If it is, only update if modified OR just keep it.
                    if (dbEntity == null || dbEntity.lastModified != date) {
                        val name = cursor.getString(idName)
                        val size = cursor.getLong(idSize)
                        val type = cursor.getString(idType)
                        val isTrashed = if (idTrashed != -1) cursor.getInt(idTrashed) == 1 else false
                        
                        val isLocked = isPdfLocked(path)
                        val thumbnailFile = thumbnailManager.getThumbnailFile(path)
                        val thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null

                        scannedFiles.add(
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
                                isFavorite = dbEntity?.isFavorite ?: false, // Preserve favorite status
                                lastOpened = dbEntity?.lastOpened ?: 0L,     // Preserve last opened status
                                thumbnailPath = thumbnailPath
                            )
                        )
                    }
                }
            }
            
            // Insert new/updated files
            if (scannedFiles.isNotEmpty()) {
                pdfDao.upsertPdfs(scannedFiles)
                // Fire and forget indexing for new files
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    scannedFiles.forEach { pdf ->
                        if (!pdf.isLocked) {
                            textExtractor.extractAndIndexPdf(pdf.path)
                        }
                    }
                }
            }
            
            // Remove deleted files
            if (scannedPaths.isNotEmpty()) {
                pdfDao.deleteStalePdfs(scannedPaths)
            }
        }
    }

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) {
        pdfDao.updateFavorite(path, isFavorite)
    }

    override suspend fun updateLastOpened(path: String, timestamp: Long) {
        pdfDao.updateLastOpened(path, timestamp)
    }

    override suspend fun deleteFileByPath(path: String) {
        pdfDao.deleteByPath(path)
    }

    override suspend fun searchPdfText(query: String): List<com.pdf.pdfreader.data.local.SearchResult> {
        return pdfDao.searchPdfText(query)
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
        isFavorite = isFavorite,
        lastOpened = lastOpened,
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
        return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
