package com.pdf.pdfreader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.domain.model.PdfFile
import com.pdf.pdfreader.domain.repository.PdfRepository
import com.pdf.pdfreader.utiles.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentUiState(
    val recentFiles: List<PdfFile> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    val thumbnailManager: ThumbnailManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentUiState())
    val uiState: StateFlow<RecentUiState> = _uiState.asStateFlow()

    init {
        observeRecents()
    }

    private fun observeRecents() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            pdfRepository.getRecentPdfs().collect { files ->
                _uiState.update { it.copy(recentFiles = files, isLoading = false) }
            }
        }
    }

    fun markAsOpened(path: String) {
        viewModelScope.launch {
            pdfRepository.updateLastOpened(path, System.currentTimeMillis())
        }
    }
}
