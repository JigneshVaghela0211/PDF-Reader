package com.pdf.pdfreader.domain.repository

import com.pdf.pdfreader.domain.model.PdfFile
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    fun getPdfFiles(): Flow<List<PdfFile>>
    fun getFavoritePdfs(): Flow<List<PdfFile>>
    fun getRecentPdfs(): Flow<List<PdfFile>>
    suspend fun syncFilesWithStorage()
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updateLastOpened(path: String, timestamp: Long)
    suspend fun updateLastOpenedPage(path: String, page: Int)
    suspend fun getBookmarksForPdf(path: String): Flow<List<com.pdf.pdfreader.data.local.BookmarkEntity>>
    suspend fun addBookmark(path: String, pageIndex: Int, label: String? = null)
    suspend fun removeBookmark(path: String, pageIndex: Int)
    suspend fun isBookmarked(path: String, pageIndex: Int): Boolean
    suspend fun deleteFileByPath(path: String)
    suspend fun renameFile(oldPath: String, newName: String): Boolean
    suspend fun duplicateFile(path: String): Boolean
    suspend fun moveFile(oldPath: String, targetDir: String): Boolean
    suspend fun deleteFileCompletely(path: String): Boolean
    suspend fun searchPdfText(query: String): List<com.pdf.pdfreader.data.local.SearchResult>
}
