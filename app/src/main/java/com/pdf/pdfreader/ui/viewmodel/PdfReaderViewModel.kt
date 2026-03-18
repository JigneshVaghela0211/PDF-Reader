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
    val visiblePages: Map<Int, Bitmap?> = emptyMap()
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState = _uiState.asStateFlow()

    private var pageRenderer: PdfPageRenderer? = null
    
    // Memory Cache for Bitmaps: 1/8 of available memory
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) {
                // If we want to be extremely careful, we could recycle here, 
                // but Android 3.0+ handles it automatically.
            }
        }
    }

    fun initialize(path: String, password: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                filePath = path, 
                fileName = File(path).name, 
                isLoading = true, 
                errorMessage = null,
                password = password ?: ""
            ) }
            
            try {
                withContext(Dispatchers.IO) {
                    // Close previous renderer if any
                    pageRenderer?.close()
                    bitmapCache.evictAll()
                    
                    pageRenderer = PdfPageRenderer(getApplication(), path, password)
                    val count = pageRenderer?.pageCount ?: 0
                    
                    _uiState.update { state -> 
                        state.copy(
                            totalPages = count,
                            isLoading = false,
                            isPasswordProtected = false,
                            isPasswordPromptVisible = false,
                            isPasswordCorrect = true
                        ) 
                    }
                }
            } catch (e: SecurityException) {
                // If we tried with a password and got a SecurityException, it's the wrong password.
                _uiState.update { it.copy(
                    isPasswordProtected = true,
                    isPasswordPromptVisible = true,
                    isPasswordCorrect = password == null, // false if password was tried
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to open PDF: ${e.localizedMessage}", isLoading = false) }
            }
        }
    }

    fun onPageVisible(index: Int) {
        if (index < 0 || index >= _uiState.value.totalPages) return
        
        // Update current page number
        _uiState.update { it.copy(currentPage = index) }

        // Check if already in cache
        val cachedBitmap = bitmapCache.get(index)
        if (cachedBitmap != null) {
            updateVisiblePage(index, cachedBitmap)
            return
        }

        // Render if not in cache
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                // Determine width from screen or use a default high-quality width
                pageRenderer?.renderPage(index, 1080)
            }
            if (bitmap != null) {
                bitmapCache.put(index, bitmap)
                updateVisiblePage(index, bitmap)
            }
        }
    }
    
    private fun updateVisiblePage(index: Int, bitmap: Bitmap) {
        _uiState.update { state ->
            val newVisible = state.visiblePages.toMutableMap()
            newVisible[index] = bitmap
            
            // Keep only a few neighbors in UI state to save Compose memory
            val keysToRemove = newVisible.keys.filter { it < index - 2 || it > index + 2 }
            keysToRemove.forEach { newVisible.remove(it) }
            
            state.copy(visiblePages = newVisible)
        }
    }

    fun submitPassword(password: String) {
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Android 15+ supports native unlocking
            initialize(_uiState.value.filePath, password)
        } else {
            // Older versions cannot unlock natively
            _uiState.update { it.copy(
                errorMessage = "Native Android PDF renderer only supports password protected files on Android 15 (API 35)+. For older versions, a professional library like Pdfium or PDF.js (via WebView) is required.",
                isLoading = false
            ) }
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, isPasswordCorrect = true) }
    }

    fun togglePasswordPrompt(visible: Boolean) {
        _uiState.update { it.copy(isPasswordPromptVisible = visible) }
    }

    fun updateCurrentPage(index: Int) {
        if (index < 0 || index >= _uiState.value.totalPages) return
        if (_uiState.value.currentPage != index) {
            _uiState.update { it.copy(currentPage = index) }
            onPageVisible(index)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pageRenderer?.close()
    }
}
