package com.pdf.pdfreader.domain.model

/**
 * Reading direction for the PDF viewer.
 */
enum class ReadingMode {
    VERTICAL,   // LazyColumn — top-to-bottom scroll
    HORIZONTAL  // LazyRow — left-to-right swipe
}

/**
 * Background / color filter applied to PDF pages.
 */
enum class BackgroundMode {
    ORIGINAL,     // No filter (white background)
    PAPER,        // Warm cream tone
    EYE_COMFORT,  // Sepia / yellowish tint
    INVERT        // Full inversion (dark / night mode)
}

/**
 * Centralized view settings for the PDF reader.
 * Persisted via DataStore so they survive app restarts.
 */
data class ViewSettings(
    val readingMode: ReadingMode = ReadingMode.VERTICAL,
    val backgroundMode: BackgroundMode = BackgroundMode.ORIGINAL,
    val isPageSnap: Boolean = false,
    val keepScreenOn: Boolean = false
)
