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
import com.pdf.pdfreader.ui.viewmodel.TextSelectionState
import com.pdf.pdfreader.selection.hit.PdfWordHitTester

private const val TAG = "TextSelectionOverlay"

private val HANDLE_COLOR = Color(0xFF2196F3)

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

    // Extract all words and sort them in reading order (top to bottom, left to right).
    val allWords = remember(textBlocks) {
        textBlocks
            .flatMap { it.words }
            .sortedWith(compareBy({ it.y }, { it.x }))
    }

    fun findWordAt(offset: Offset): TextWord? =
        PdfWordHitTester.wordAt(offset, pageWidth, pageHeight, allWords)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Long-press to select a word, then drag (still in the same gesture) extends the end.
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
                            editorViewModel.clearTextSelection()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val word = findWordAt(change.position)
                        if (word != null && textSelection?.pageIndex == pageIndex) {
                            editorViewModel.moveSelectionEnd(pageIndex, word, allWords)
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
        // Highlight + draggable handles (shared with the Edit-Text overlay).
        TextSelectionVisuals(
            pageIndex = pageIndex,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            textSelection = textSelection,
            allWords = allWords,
            editorViewModel = editorViewModel
        )
    }
}

/**
 * Renders the word-selection highlight and the two draggable start/end handles for the
 * active [textSelection]. Extracted so both reading-mode ([TextSelectionOverlay]) and
 * Edit-Text mode reuse one implementation instead of duplicating the handle math. Must be
 * placed inside a full-size parent; it fills that parent.
 */
@Composable
internal fun TextSelectionVisuals(
    pageIndex: Int,
    pageWidth: Int,
    pageHeight: Int,
    textSelection: TextSelectionState?,
    allWords: List<TextWord>,
    editorViewModel: com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel
) {
    if (textSelection?.pageIndex != pageIndex || textSelection.selectedWords.isEmpty()) return
    if (pageWidth <= 0 || pageHeight <= 0) return

    fun findWordAt(offset: Offset): TextWord? =
        PdfWordHitTester.wordAt(offset, pageWidth, pageHeight, allWords)

    val selectedRects = textSelection.selectedWords.map { word ->
        Rect(
            left = word.x * pageWidth,
            top = word.y * pageHeight,
            right = (word.x + word.width) * pageWidth,
            bottom = (word.y + word.height) * pageHeight
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            selectedRects.forEach { rect ->
                drawRect(color = HANDLE_COLOR.copy(alpha = 0.3f), topLeft = rect.topLeft, size = rect.size)
            }
        }

        val startWord = textSelection.startWord ?: textSelection.selectedWords.first()
        val endWord = textSelection.endWord ?: textSelection.selectedWords.last()

        SelectionHandle(
            centerX = startWord.x * pageWidth,
            centerY = (startWord.y + startWord.height) * pageHeight,
            color = HANDLE_COLOR,
            onDrag = { pageOffset ->
                findWordAt(pageOffset)?.let { editorViewModel.moveSelectionStart(pageIndex, it, allWords) }
            },
            onDragEnd = { editorViewModel.finalizeTextSelection() }
        )

        SelectionHandle(
            centerX = (endWord.x + endWord.width) * pageWidth,
            centerY = (endWord.y + endWord.height) * pageHeight,
            color = HANDLE_COLOR,
            onDrag = { pageOffset ->
                findWordAt(pageOffset)?.let { editorViewModel.moveSelectionEnd(pageIndex, it, allWords) }
            },
            onDragEnd = { editorViewModel.finalizeTextSelection() }
        )
    }
}
