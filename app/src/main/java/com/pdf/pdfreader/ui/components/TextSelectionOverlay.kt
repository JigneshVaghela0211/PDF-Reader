package com.pdf.pdfreader.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.pdf.pdfreader.domain.model.InteractionMode
import com.pdf.pdfreader.domain.model.TextBlock
import com.pdf.pdfreader.domain.model.TextWord
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import com.pdf.pdfreader.ui.viewmodel.TextSelectionState

private const val TAG = "TextSelectionOverlay"

@Composable
fun TextSelectionOverlay(
    modifier: Modifier = Modifier,
    pageIndex: Int,
    pageWidth: Int,
    pageHeight: Int,
    textBlocks: List<TextBlock>,
    interactionMode: InteractionMode,
    textSelection: TextSelectionState?,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel
) {
    if (pageWidth <= 0 || pageHeight <= 0) return

    val density = androidx.compose.ui.platform.LocalDensity.current

    // Extract all words and sort them structurally (top to bottom, left to right)
    val allWords = remember(textBlocks) {
        textBlocks
            .flatMap { it.words }
            .sortedWith(compareBy({ it.y }, { it.x }))
    }

    // Hit testing function
    fun findWordAt(offset: Offset): TextWord? {
        val normX = offset.x / pageWidth
        val normY = offset.y / pageHeight
        // Small padding for easier tap target
        val touchPadding = 15f / pageWidth 

        return allWords.firstOrNull { word ->
            normX >= word.x - touchPadding && normX <= word.x + word.width + touchPadding &&
            normY >= word.y - touchPadding && normY <= word.y + word.height + touchPadding
        }
    }

    // Gather bounding rects for the selected words
    val selectedRects = remember(textSelection) {
        if (textSelection?.pageIndex == pageIndex) {
            textSelection.selectedWords.map { word ->
                Rect(
                    left = word.x * pageWidth,
                    top = word.y * pageHeight,
                    right = (word.x + word.width) * pageWidth,
                    bottom = (word.y + word.height) * pageHeight
                )
            }
        } else {
            emptyList()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Gesture listener
            .pointerInput(allWords, interactionMode) {
                if (interactionMode != InteractionMode.NONE && interactionMode != InteractionMode.SELECT_TEXT) {
                    return@pointerInput
                }

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val word = findWordAt(offset)
                        if (word != null) {
                            Log.d(TAG, "Selection gesture started at word: ${word.text}")
                            editorViewModel.startTextSelection(pageIndex, word, allWords)
                        } else {
                            // If user long presses empty space, maybe clear selection?
                            editorViewModel.clearTextSelection()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val word = findWordAt(change.position)
                        if (word != null && textSelection?.pageIndex == pageIndex) {
                            editorViewModel.updateTextSelection(pageIndex, word, allWords)
                        }
                    },
                    onDragEnd = {
                        Log.d(TAG, "Selection gesture ended")
                        editorViewModel.finalizeTextSelection()
                    },
                    onDragCancel = {
                        editorViewModel.clearTextSelection()
                    }
                )
            }
            .pointerInput(interactionMode) {
                detectTapGestures(
                    onTap = {
                        if (interactionMode == InteractionMode.SELECT_TEXT) {
                            editorViewModel.clearTextSelection()
                        }
                    }
                )
            }
    ) {
        // Draw selection highlights
        if (selectedRects.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                selectedRects.forEach { rect ->
                    drawRect(
                        color = Color(0xFF2196F3).copy(alpha = 0.3f),
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                }
            }
        }
    }
}
