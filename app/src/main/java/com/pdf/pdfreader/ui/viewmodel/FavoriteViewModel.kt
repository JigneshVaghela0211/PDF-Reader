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

data class FavoriteUiState(
    val favoriteFiles: List<PdfFile> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    val thumbnailManager: ThumbnailManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            pdfRepository.getFavoritePdfs().collect { files ->
                _uiState.update { it.copy(favoriteFiles = files, isLoading = false) }
            }
        }
    }

    fun toggleFavorite(pdf: PdfFile) {
        viewModelScope.launch {
            pdfRepository.updateFavorite(pdf.path, !pdf.isFavorite)
        }
    }
}
