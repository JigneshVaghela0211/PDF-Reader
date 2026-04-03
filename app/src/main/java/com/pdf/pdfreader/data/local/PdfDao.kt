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

    @Query("UPDATE pdf_files SET lastOpenedPage = :page WHERE path = :path")
    suspend fun updateLastOpenedPage(path: String, page: Int)

    @Query("DELETE FROM pdf_files WHERE path NOT IN (:remainingPaths)")
    suspend fun deleteStalePdfs(remainingPaths: List<String>)

    @Query("UPDATE pdf_files SET thumbnailPath = :thumbnailPath WHERE path = :pdfPath")
    suspend fun updateThumbnail(pdfPath: String, thumbnailPath: String?)

    @Query("DELETE FROM pdf_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextSnippets(snippets: List<PdfTextSnippet>)

    @Query("DELETE FROM pdf_text_search WHERE pdfPath = :path")
    suspend fun deleteTextSnippetsByPath(path: String)

    @Query("""
        SELECT pdf_files.name as fileName, 
               pdf_text_search.pdfPath, 
               pdf_text_search.pageIndex, 
               snippet(pdf_text_search, '<b>', '</b>', '...', -1, 40) as snippet
        FROM pdf_text_search
        JOIN pdf_files ON pdf_text_search.pdfPath = pdf_files.path
        WHERE pdf_text_search MATCH :query
        LIMIT 50
    """)
    suspend fun searchPdfText(query: String): List<SearchResult>
}
