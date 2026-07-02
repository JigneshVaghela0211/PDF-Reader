package com.pdf.pdfreader.feature.pdf_ocr.domain.usecase

import com.pdf.pdfreader.feature.pdf_ocr.data.engine.SearchableLayerWriter
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.MakeSearchableEvent
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.repository.OcrRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * The full "Make Searchable" pipeline: recognize every page (cache-aware, so a
 * cancelled run resumes where it stopped), then write the invisible text layer
 * into a sibling `*_ocr.pdf`. Emits per-page [MakeSearchableEvent.Progress] and a
 * terminal [MakeSearchableEvent.Done].
 */
class MakeSearchablePdfUseCase @Inject constructor(
    private val repository: OcrRepository,
    private val layerWriter: SearchableLayerWriter
) {
    operator fun invoke(
        path: String,
        script: OcrScript,
        force: Boolean = false
    ): Flow<MakeSearchableEvent> = flow {
        repository.recognizeDocument(path, script, force).collect {
            emit(MakeSearchableEvent.Progress(it))
        }
        val document = repository.cachedDocument(path, script)
        val result = document?.let { layerWriter.writeSearchablePdf(path, it) }
        emit(MakeSearchableEvent.Done(result))
    }
}
