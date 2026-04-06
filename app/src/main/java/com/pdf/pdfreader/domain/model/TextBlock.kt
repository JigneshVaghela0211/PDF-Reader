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
    val fontName: String = "Helvetica"
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
    val newColor: Color = Color.Black
)
