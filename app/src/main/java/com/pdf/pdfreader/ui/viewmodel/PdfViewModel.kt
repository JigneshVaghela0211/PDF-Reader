package com.pdf.pdfreader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.domain.usecase.GetPdfFilesUseCase
import com.pdf.pdfreader.domain.usecase.RefreshPdfFilesUseCase
import com.pdf.pdfreader.utiles.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfUiState(
    val pdfFiles: List<PdfFile> = emptyList(),
    val filteredFiles: List<PdfFile> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val getPdfFilesUseCase: GetPdfFilesUseCase,
    private val refreshPdfFilesUseCase: RefreshPdfFilesUseCase,
    val thumbnailManager: ThumbnailManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    init {
        observePdfFiles()
    }

    private fun observePdfFiles() {
        viewModelScope.launch {
            getPdfFilesUseCase().collect { files ->
                _uiState.update { state -> 
                    state.copy(
                        pdfFiles = files,
                        filteredFiles = filterFiles(files, state.searchQuery)
                    ) 
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredFiles = filterFiles(state.pdfFiles, query)
            )
        }
    }

    private fun filterFiles(files: List<PdfFile>, query: String): List<PdfFile> {
        return if (query.isEmpty()) {
            files
        } else {
            files.filter { 
                it.name.contains(query, ignoreCase = true) 
            }
        }
    }

    fun loadPdfFiles(isInitialLoad: Boolean = true) {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update { 
                if (isInitialLoad && currentState.pdfFiles.isEmpty()) {
                    it.copy(isLoading = true, errorMessage = null)
                } else {
                    it.copy(isRefreshing = true, errorMessage = null)
                }
            }
            try {
                refreshPdfFilesUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load PDF files: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}
