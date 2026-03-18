package com.pdf.pdfreader.domain.repository

import com.pdf.pdfreader.domain.model.PdfFile
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    fun getPdfFiles(): Flow<List<PdfFile>>
    fun getFavoritePdfs(): Flow<List<PdfFile>>
    fun getRecentPdfs(): Flow<List<PdfFile>>
    suspend fun refreshPdfFiles()
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updateLastOpened(path: String, timestamp: Long)
}
