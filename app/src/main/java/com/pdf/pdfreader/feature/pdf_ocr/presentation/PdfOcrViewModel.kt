package com.pdf.pdfreader.feature.pdf_ocr.presentation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.feature.pdf_ocr.domain.OcrToTextBlockMapper
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.MakeSearchableEvent
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrEditableWord
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrWordEdit
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PdfDocumentClassification
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.PdfDocumentClassification.DocKind
import com.pdf.pdfreader.feature.pdf_ocr.domain.usecase.AnalyzePdfDocumentUseCase
import com.pdf.pdfreader.feature.pdf_ocr.domain.usecase.ExportOcrEditedPdfUseCase
import com.pdf.pdfreader.feature.pdf_ocr.domain.usecase.IndexOcrTextUseCase
import com.pdf.pdfreader.feature.pdf_ocr.domain.usecase.MakeSearchablePdfUseCase
import com.pdf.pdfreader.feature.pdf_ocr.domain.usecase.RecognizePageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of a long-running OCR job. Deliberately separate from the shared
 * [com.pdf.pdfreader.ui.viewmodel.ToolStatus] (merge/split/compress): OCR needs
 * fractional per-page progress and cancellation.
 */
sealed interface OcrRunState {
    data object Idle : OcrRunState
    data class Running(
        val label: String,
        val currentPage: Int,
        val totalPages: Int,
        val fraction: Float
    ) : OcrRunState

    data class Success(val message: String, val outputPath: String?) : OcrRunState
    data class Error(val message: String) : OcrRunState
}

/**
 * State of the OCR word-editing mode for scanned pages. Fully separate from
 * PdfEditorUiState: OCR edits never enter the real text-editing pipeline.
 */
data class OcrEditUiState(
    val isOcrEditMode: Boolean = false,
    val path: String? = null,
    val script: OcrScript = OcrScript.LATIN,
    /** Page currently being recognized, or null when idle. */
    val recognizingPage: Int? = null,
    /** Page index → editable OCR words (only OCR'd pages are present). */
    val words: Map<Int, List<OcrEditableWord>> = emptyMap(),
    /** Word id → edit. */
    val edits: Map<String, OcrWordEdit> = emptyMap(),
    val selectedWordId: String? = null,
    val lowConfidenceThreshold: Float = 0.5f,
    val error: String? = null
)

/**
 * Presentation state for the OCR feature. Deliberately separate from
 * [com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel]: OCR state must never mix
 * with the real text-editing state, and all OCR logic stays behind use cases.
 *
 * Owns three concerns: (1) document analysis + the "OCR required" decision
 * dialog, (2) long-running OCR jobs (Make Searchable / save) via [runState],
 * (3) the word-editing mode for scanned pages via [ocrEditState].
 */
@HiltViewModel
class PdfOcrViewModel @Inject constructor(
    private val analyzePdfDocument: AnalyzePdfDocumentUseCase,
    private val makeSearchablePdf: MakeSearchablePdfUseCase,
    private val recognizePage: RecognizePageUseCase,
    private val exportOcrEditedPdf: ExportOcrEditedPdfUseCase,
    private val indexOcrText: IndexOcrTextUseCase
) : ViewModel() {

    /** Classification of the currently open document; null while analysis runs. */
    private val _classification = MutableStateFlow<PdfDocumentClassification?>(null)
    val classification = _classification.asStateFlow()

    private val _showOcrRequiredDialog = MutableStateFlow(false)
    val showOcrRequiredDialog = _showOcrRequiredDialog.asStateFlow()

    private var analyzedPath: String? = null
    private var analyzeJob: Job? = null

    /**
     * Kick off (memoized) document analysis so the Edit Text tap can branch without
     * waiting. Safe to call repeatedly; re-analyzes only when the path changes.
     */
    fun prepare(filePath: String) {
        if (!PdfEditorFeatureConfig.ENABLE_OCR_EDIT) return
        if (filePath == analyzedPath) return
        analyzedPath = filePath
        _classification.value = null
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _classification.value = analyzePdfDocument(filePath)
        }
    }

    /**
     * Called when the user activates the Edit Text tool. Raises the "OCR required"
     * dialog when the whole document is scanned or the page being viewed is
     * image-only. No-op (existing behavior) for searchable documents/pages.
     */
    fun onEditTextRequested(filePath: String, pageIndex: Int) {
        if (!PdfEditorFeatureConfig.ENABLE_OCR_EDIT) return
        viewModelScope.launch {
            prepare(filePath)
            analyzeJob?.join()
            val classification = _classification.value ?: return@launch
            if (classification.kind == DocKind.SCANNED || classification.isPageScanned(pageIndex)) {
                _showOcrRequiredDialog.value = true
            }
        }
    }

    fun dismissOcrRequiredDialog() {
        _showOcrRequiredDialog.value = false
    }

    // ─── Document OCR run (Make Searchable) ───────────────────────

    private val _runState = MutableStateFlow<OcrRunState>(OcrRunState.Idle)
    val runState = _runState.asStateFlow()

    private var ocrJob: Job? = null

    /**
     * OCR the whole document and write the invisible-layer `*_ocr.pdf`. Cached
     * pages are skipped, so re-running after a cancel resumes where it stopped.
     */
    fun runDocumentOcr(path: String, script: OcrScript) {
        if (ocrJob?.isActive == true) return
        _runState.value = OcrRunState.Running("Recognizing text…", 0, 0, 0f)
        ocrJob = viewModelScope.launch {
            try {
                makeSearchablePdf(path, script).collect { event ->
                    when (event) {
                        is MakeSearchableEvent.Progress -> _runState.value = OcrRunState.Running(
                            label = "Recognizing text…",
                            currentPage = event.progress.currentPage,
                            totalPages = event.progress.totalPages,
                            fraction = event.progress.fraction
                        )
                        is MakeSearchableEvent.Done -> {
                            _runState.value = when {
                                event.result == null -> OcrRunState.Error("OCR failed")
                                event.result.wordsAdded == 0 ->
                                    OcrRunState.Success("No text recognized on these pages", event.result.outputPath)
                                else -> OcrRunState.Success(
                                    "Searchable PDF created · ${event.result.wordsAdded} words on " +
                                        "${event.result.pagesProcessed} pages",
                                    event.result.outputPath
                                )
                            }
                            // Make the scanned doc findable via in-app search (no-op unless flagged).
                            runCatching { indexOcrText(path, script) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _runState.value = OcrRunState.Idle
                throw e
            } catch (e: Exception) {
                _runState.value = OcrRunState.Error(e.message ?: "OCR failed")
            }
        }
    }

    /** Stop the current run. Finished pages stay cached; the next run resumes. */
    fun cancelOcr() {
        ocrJob?.cancel()
        _runState.value = OcrRunState.Idle
    }

    fun resetRunState() {
        _runState.value = OcrRunState.Idle
    }

    // ─── OCR edit mode (word-level editing of scanned pages) ─────

    private val _ocrEditState = MutableStateFlow(OcrEditUiState())
    val ocrEditState = _ocrEditState.asStateFlow()

    private var editRecognizeJob: Job? = null

    /** OCR the given page (cache-first) and enter word-editing mode on it. */
    fun enterOcrEditMode(path: String, pageIndex: Int, script: OcrScript) {
        if (!PdfEditorFeatureConfig.ENABLE_OCR_EDIT) return
        editRecognizeJob?.cancel()
        _ocrEditState.value = OcrEditUiState(
            isOcrEditMode = true,
            path = path,
            script = script,
            recognizingPage = pageIndex
        )
        editRecognizeJob = viewModelScope.launch {
            try {
                val page = recognizePage(path, pageIndex, script)
                _ocrEditState.update {
                    it.copy(
                        recognizingPage = null,
                        words = it.words + (pageIndex to OcrToTextBlockMapper.toEditableWords(page)),
                        error = if (page.wordCount == 0) "No text recognized — try a different language" else null
                    )
                }
                runCatching { indexOcrText(path, script) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ocrEditState.update {
                    it.copy(recognizingPage = null, error = e.message ?: "OCR failed")
                }
            }
        }
    }

    fun selectOcrWord(id: String?) {
        _ocrEditState.update { it.copy(selectedWordId = id) }
    }

    fun editOcrWord(id: String, newText: String, newFontSize: Float, newColor: Color) {
        _ocrEditState.update { state ->
            val word = state.words.values.flatten().find { it.id == id } ?: return@update state
            val edit = OcrWordEdit(
                id = id,
                pageIndex = word.pageIndex,
                originalWord = word.word,
                newText = newText,
                fontSizePdf = newFontSize,
                color = newColor
            )
            state.copy(edits = state.edits + (id to edit), selectedWordId = null)
        }
    }

    fun clearOcrEditError() {
        _ocrEditState.update { it.copy(error = null) }
    }

    /**
     * Export the current OCR edits as a new searchable PDF (patch + visible text
     * for edited words, invisible layer for the rest). Progress/result surface
     * through [runState] like the other OCR jobs.
     */
    fun saveOcrEdits(customName: String? = null) {
        if (!PdfEditorFeatureConfig.ENABLE_OCR_EXPORT) return
        val state = _ocrEditState.value
        val path = state.path ?: return
        if (state.edits.isEmpty()) {
            _ocrEditState.update { it.copy(error = "Nothing to save yet") }
            return
        }
        if (ocrJob?.isActive == true) return
        _runState.value = OcrRunState.Running("Saving OCR edits…", 0, 0, 0f)
        ocrJob = viewModelScope.launch {
            try {
                val result = exportOcrEditedPdf(path, state.script, state.edits.values.toList(), customName)
                _runState.value = if (result == null) {
                    OcrRunState.Error("Save failed")
                } else {
                    OcrRunState.Success(
                        "Saved · ${result.editsApplied} edit(s), ${result.invisibleWordsAdded} searchable words",
                        result.outputPath
                    )
                }
            } catch (e: CancellationException) {
                _runState.value = OcrRunState.Idle
                throw e
            } catch (e: Exception) {
                _runState.value = OcrRunState.Error(e.message ?: "Save failed")
            }
        }
    }

    fun exitOcrEditMode() {
        editRecognizeJob?.cancel()
        _ocrEditState.value = OcrEditUiState()
    }
}
