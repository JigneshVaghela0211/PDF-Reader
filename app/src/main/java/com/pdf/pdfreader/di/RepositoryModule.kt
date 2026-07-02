package com.pdf.pdfreader.di

import com.pdf.pdfreader.data.repository.PdfRepositoryImpl
import com.pdf.pdfreader.data.repository.UndoRedoRepositoryImpl
import com.pdf.pdfreader.domain.repository.PdfRepository
import com.pdf.pdfreader.domain.repository.UndoRedoRepository
import com.pdf.pdfreader.feature.pdf_ocr.data.repository.OcrRepositoryImpl
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPdfRepository(
        pdfRepositoryImpl: PdfRepositoryImpl
    ): PdfRepository

    @Binds
    @Singleton
    abstract fun bindUndoRedoRepository(
        undoRedoRepositoryImpl: UndoRedoRepositoryImpl
    ): UndoRedoRepository

    @Binds
    @Singleton
    abstract fun bindOcrRepository(
        ocrRepositoryImpl: OcrRepositoryImpl
    ): OcrRepository
}
