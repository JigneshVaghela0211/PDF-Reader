package com.pdf.pdfreader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_files ORDER BY lastModified DESC")
    fun getAllPdfs(): Flow<List<PdfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfs(pdfs: List<PdfEntity>)

    @Query("DELETE FROM pdf_files")
    suspend fun deleteAllPdfs()

    @Transaction
    suspend fun syncPdfs(pdfs: List<PdfEntity>) {
        deleteAllPdfs()
        insertPdfs(pdfs)
    }

    @Query("UPDATE pdf_files SET thumbnailPath = :thumbnailPath WHERE path = :pdfPath")
    suspend fun updateThumbnail(pdfPath: String, thumbnailPath: String?)
}
