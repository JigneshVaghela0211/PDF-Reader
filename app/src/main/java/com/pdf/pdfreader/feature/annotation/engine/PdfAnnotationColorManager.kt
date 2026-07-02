package com.pdf.pdfreader.feature.annotation.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pdf.pdfreader.data.local.PreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the "recent markup colors" list and the default markup color, backed by
 * [PreferenceManager] (DataStore) so recents survive app restarts. Kept out of the
 * ViewModel so color persistence/policy has one home. Colors are stored as packed
 * ARGB ints.
 */
@Singleton
class PdfAnnotationColorManager @Inject constructor(
    private val preferenceManager: PreferenceManager
) {

    /** Recently-used markup colors, newest first. */
    val recentColorsFlow: Flow<List<Color>> =
        preferenceManager.recentMarkupColorsFlow.map { argbList -> argbList.map { Color(it) } }

    /** Record [color] as most-recent (dedup, capped), persisting the list. */
    suspend fun remember(color: Color) {
        val argb = color.toArgb()
        val current = preferenceManager.recentMarkupColorsFlow.first()
        val updated = (listOf(argb) + current.filter { it != argb }).take(MAX_RECENT)
        preferenceManager.saveRecentMarkupColors(updated)
    }

    companion object {
        /** Default markup color (yellow) before the user picks one. */
        val DEFAULT_MARKUP_COLOR = Color(0xFFFDD835)
        private const val MAX_RECENT = 8
    }
}
