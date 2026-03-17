package com.pdf.pdfreader.domain.repository

import com.pdf.pdfreader.domain.model.PdfFile
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    fun getPdfFiles(): Flow<List<PdfFile>>
    suspend fun refreshPdfFiles()
}
