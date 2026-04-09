package com.pdf.pdfreader.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

sealed class PdfAnnotation {
    abstract val id: String
    abstract val pageIndex: Int
    
    data class Path(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
        val isHighlighter: Boolean = false
    ) : PdfAnnotation()
    
    data class TextNote(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val text: String,
        val position: Offset,
        val color: Color,
        val fontSize: Float
    ) : PdfAnnotation()
    
    enum class MarkupType {
        HIGHLIGHT, UNDERLINE, STRIKETHROUGH
    }

    data class TextMarkup(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val rects: List<androidx.compose.ui.geometry.Rect>,
        val color: Color,
        val type: MarkupType
    ) : PdfAnnotation()
}
