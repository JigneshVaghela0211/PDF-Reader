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

enum class SortType { LAST_MODIFIED, NAME, FILE_SIZE }
enum class SortOrder { NEW_TO_OLD, OLD_TO_NEW }
enum class ViewMode { LIST, GRID }

data class PdfUiState(
    val pdfFiles: List<PdfFile> = emptyList(),
    val filteredFiles: List<PdfFile> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortType: SortType = SortType.LAST_MODIFIED,
    val sortOrder: SortOrder = SortOrder.NEW_TO_OLD
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
                        filteredFiles = processFiles(files, state.searchQuery, state.sortType, state.sortOrder)
                    ) 
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredFiles = processFiles(state.pdfFiles, query, state.sortType, state.sortOrder)
            )
        }
    }

    fun updateSortSettings(sortType: SortType, sortOrder: SortOrder) {
        _uiState.update { state ->
            state.copy(
                sortType = sortType,
                sortOrder = sortOrder,
                filteredFiles = processFiles(state.pdfFiles, state.searchQuery, sortType, sortOrder)
            )
        }
    }

    fun onViewModeChange(viewMode: ViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    private fun processFiles(
        files: List<PdfFile>, 
        query: String, 
        sortType: SortType, 
        sortOrder: SortOrder
    ): List<PdfFile> {
        val filtered = if (query.isEmpty()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }

        return when (sortType) {
            SortType.NAME -> if (sortOrder == SortOrder.NEW_TO_OLD) filtered.sortedByDescending { it.name } else filtered.sortedBy { it.name }
            SortType.FILE_SIZE -> if (sortOrder == SortOrder.NEW_TO_OLD) filtered.sortedByDescending { it.size } else filtered.sortedBy { it.size }
            SortType.LAST_MODIFIED -> if (sortOrder == SortOrder.NEW_TO_OLD) filtered.sortedByDescending { it.lastModified } else filtered.sortedBy { it.lastModified }
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
