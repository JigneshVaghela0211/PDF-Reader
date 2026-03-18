package com.pdf.pdfreader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_files ORDER BY lastModified DESC")
    fun getAllPdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files WHERE isFavorite = 1 ORDER BY lastModified DESC")
    fun getFavoritePdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files WHERE lastOpened > 0 ORDER BY lastOpened DESC")
    fun getRecentPdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files")
    suspend fun getAllPdfsOnce(): List<PdfEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPdfs(pdfs: List<PdfEntity>)

    @Query("UPDATE pdf_files SET isFavorite = :isFavorite WHERE path = :path")
    suspend fun updateFavorite(path: String, isFavorite: Boolean)

    @Query("UPDATE pdf_files SET lastOpened = :timestamp WHERE path = :path")
    suspend fun updateLastOpened(path: String, timestamp: Long)

    @Query("DELETE FROM pdf_files WHERE path NOT IN (:remainingPaths)")
    suspend fun deleteStalePdfs(remainingPaths: List<String>)

    @Query("UPDATE pdf_files SET thumbnailPath = :thumbnailPath WHERE path = :pdfPath")
    suspend fun updateThumbnail(pdfPath: String, thumbnailPath: String?)
}
