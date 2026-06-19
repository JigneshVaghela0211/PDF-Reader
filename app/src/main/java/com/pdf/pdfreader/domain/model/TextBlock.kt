package com.pdf.pdfreader.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Represents a block of text extracted from a PDF page.
 * Coordinates are in PDF space (origin at bottom-left), normalized 0..1 for rendering.
 */
data class TextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val text: String,
    /** Normalized x position (0..1 relative to page width) */
    val x: Float,
    /** Normalized y position (0..1 relative to page height) */
    val y: Float,
    /** Normalized width (0..1) */
    val width: Float,
    /** Normalized height (0..1) */
    val height: Float,
    /** Approximate font size in PDF points */
    val fontSize: Float,
    /** Font name from PDF metadata */
    val fontName: String = "Helvetica",
    /** The individual words and their bounding boxes */
    val words: List<TextWord> = emptyList(),
    /** Source PDF page width in points — used to scale font size from points to
     *  on-screen pixels (display px = fontSize * renderedPageWidthPx / pdfPageWidth). */
    val pdfPageWidth: Float = 0f,
    /** Source PDF page height in points. */
    val pdfPageHeight: Float = 0f
)

/**
 * Represents a single word within a text block with its normalized bounding box.
 */
data class TextWord(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * Represents a user's edit to a detected text block.
 * Stores both original and edited state for undo/redo & export.
 */
data class EditedTextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalBlock: TextBlock,
    val newText: String,
    val newFontSize: Float,
    val newColor: Color = Color.Black,
    /** Opacity from 0f (transparent) to 1f (fully opaque) */
    val opacity: Float = 1f,
    /** When true, element cannot be moved or edited */
    val isLocked: Boolean = false,
    /** Text alignment within the block */
    val alignment: TextAlignment = TextAlignment.LEFT
)

/**
 * Text alignment options for edited text blocks.
 */
enum class TextAlignment {
    LEFT, CENTER, RIGHT
}

