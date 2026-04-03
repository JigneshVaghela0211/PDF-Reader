package com.pdf.pdfreader.domain.usecase

import com.pdf.pdfreader.domain.repository.PdfRepository
import javax.inject.Inject

class SyncFilesUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    suspend operator fun invoke() {
        repository.syncFilesWithStorage()
    }
}
