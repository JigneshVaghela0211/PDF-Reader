package com.pdf.pdfreader.domain.usecase

import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPdfFilesUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    operator fun invoke(): Flow<List<PdfFile>> {
        return repository.getPdfFiles()
    }
}
