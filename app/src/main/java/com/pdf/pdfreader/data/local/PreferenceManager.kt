package com.pdf.pdfreader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pdf.pdfreader.domain.model.BackgroundMode
import com.pdf.pdfreader.domain.model.ReadingMode
import com.pdf.pdfreader.domain.model.ViewSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ─── Existing Keys ──────────────────────────────────────────
    private val themeKey = stringPreferencesKey("app_theme")
    private val languageKey = stringPreferencesKey("app_language")

    // ─── View Settings Keys ─────────────────────────────────────
    private val readingModeKey = stringPreferencesKey("reading_mode")
    private val backgroundModeKey = stringPreferencesKey("background_mode")
    private val pageSnapKey = booleanPreferencesKey("page_snap")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    // ─── Theme & Language ───────────────────────────────────────

    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[themeKey] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(themeName)
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[languageKey] ?: "en"
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme.name
        }
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[languageKey] = languageCode
        }
    }

    // ─── View Settings ──────────────────────────────────────────

    val viewSettingsFlow: Flow<ViewSettings> = context.dataStore.data.map { preferences ->
        ViewSettings(
            readingMode = preferences[readingModeKey]?.let {
                try { ReadingMode.valueOf(it) } catch (_: Exception) { ReadingMode.VERTICAL }
            } ?: ReadingMode.VERTICAL,
            backgroundMode = preferences[backgroundModeKey]?.let {
                try { BackgroundMode.valueOf(it) } catch (_: Exception) { BackgroundMode.ORIGINAL }
            } ?: BackgroundMode.ORIGINAL,
            isPageSnap = preferences[pageSnapKey] ?: false,
            keepScreenOn = preferences[keepScreenOnKey] ?: false
        )
    }

    suspend fun saveViewSettings(settings: ViewSettings) {
        context.dataStore.edit { preferences ->
            preferences[readingModeKey] = settings.readingMode.name
            preferences[backgroundModeKey] = settings.backgroundMode.name
            preferences[pageSnapKey] = settings.isPageSnap
            preferences[keepScreenOnKey] = settings.keepScreenOn
        }
    }
}
