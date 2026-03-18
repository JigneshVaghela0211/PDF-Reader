package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.LruCache
import com.pdf.pdfreader.utiles.PdfPageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PdfReaderUiState(
    val filePath: String = "",
    val fileName: String = "",
    val isPasswordProtected: Boolean = false,
    val isPasswordPromptVisible: Boolean = false,
    val password: String = "",
    val isPasswordCorrect: Boolean = true,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val zoomLevel: Float = 1f,
    val reloadTrigger: Int = 0 // Used to force a reload in the UI
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState = _uiState.asStateFlow()

    fun initialize(path: String) {
        val name = File(path).name
        _uiState.update { it.copy(filePath = path, fileName = name, isLoading = true, errorMessage = null) }
    }

    fun onLoadComplete(pages: Int) {
        _uiState.update { it.copy(totalPages = pages, isLoading = false, isPasswordProtected = false) }
    }

    fun onError(t: Throwable) {
        if (t.message?.contains("password", ignoreCase = true) == true || t is SecurityException) {
            _uiState.update { it.copy(isPasswordProtected = true, isPasswordPromptVisible = true, isLoading = false) }
        } else {
            _uiState.update { it.copy(errorMessage = t.localizedMessage, isLoading = false) }
        }
    }

    fun onPageChanged(page: Int, total: Int) {
        _uiState.update { it.copy(currentPage = page, totalPages = total) }
    }

    fun submitPassword(password: String) {
        _uiState.update { it.copy(
            password = password, 
            isPasswordPromptVisible = false, 
            isLoading = true,
            isPasswordCorrect = true,
            reloadTrigger = it.reloadTrigger + 1
        ) }
    }

    fun onPasswordCorrectness(correct: Boolean) {
        _uiState.update { it.copy(isPasswordCorrect = correct) }
    }

    fun onPasswordChange(p: String) {
        _uiState.update { it.copy(password = p) }
    }

    fun updateCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }
}
