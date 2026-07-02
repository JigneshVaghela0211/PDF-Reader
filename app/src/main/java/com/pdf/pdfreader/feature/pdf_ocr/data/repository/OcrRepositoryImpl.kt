package com.pdf.pdfreader.feature.pdf_ocr.data.repository

import com.pdf.pdfreader.feature.pdf_ocr.data.cache.OcrResultCache
import com.pdf.pdfreader.feature.pdf_ocr.data.engine.OcrRecognitionEngine
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrDocument
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrProgress
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrRepositoryImpl @Inject constructor(
    private val engine: OcrRecognitionEngine,
    private val cache: OcrResultCache
) : OcrRepository {

    override suspend fun recognizePage(
        path: String,
        pageIndex: Int,
        script: OcrScript,
        force: Boolean
    ): OcrPage {
        if (!force) {
            cache.get(path, pageIndex, script)?.let { return it }
        }
        val page = engine.recognizePage(path, pageIndex, script)
        cache.put(path, page)
        return page
    }

    override fun recognizeDocument(
        path: String,
        script: OcrScript,
        force: Boolean
    ): Flow<OcrProgress> = flow {
        val skip = if (force) emptySet() else cache.cachedPageIndices(path, script)
        engine.recognizeDocument(path, script, skipPages = skip).collect { event ->
            event.page?.let { cache.put(path, it) }
            emit(event.progress)
        }
    }

    override suspend fun cachedDocument(path: String, script: OcrScript): OcrDocument? {
        val indices = cache.cachedPageIndices(path, script)
        if (indices.isEmpty()) return null
        val pages = indices.sorted().mapNotNull { i -> cache.get(path, i, script)?.let { i to it } }
        if (pages.isEmpty()) return null
        return OcrDocument(path = path, script = script, pages = pages.toMap())
    }

    override suspend fun invalidate(path: String) = cache.invalidate(path)
}
