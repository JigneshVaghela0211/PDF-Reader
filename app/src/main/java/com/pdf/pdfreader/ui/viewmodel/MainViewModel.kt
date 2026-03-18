package com.pdf.pdfreader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.data.local.AppTheme
import com.pdf.pdfreader.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val themeState: StateFlow<AppTheme> = preferenceManager.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val languageState: StateFlow<String> = preferenceManager.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferenceManager.setTheme(theme)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            preferenceManager.setLanguage(languageCode)
        }
    }
}
