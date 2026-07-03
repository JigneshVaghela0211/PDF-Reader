package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdf.pdfreader.domain.model.TextWord
import com.pdf.pdfreader.selection.model.WordPreviewEdit

private val PreviewTextColor = Color.Black
private val CursorColor = Color(0xFF2196F3)

/**
 * Dedicated TEXT REPLACEMENT PREVIEW LAYER: for each committed [WordPreviewEdit] it draws the edited
 * text exactly over the original word's box.
 *
 * Responsibilities: render edited text · removable. It performs NO PDF modification, executes NO
 * replacement, and holds no [com.pdf.pdfreader.domain.model.EditedTextBlock].
 *
 * The original glyphs are NOT hidden here — they are already absent from the page, because the page
 * is drawn from the renderer-suppressed **edit bitmap** (see
 * [com.pdf.pdfreader.feature.reader.data.engine.SuppressingPdfRenderer]). So this layer only paints
 * the new text into the now-empty slot: no cover, no patch, no background fill. The word actively
 * being edited is skipped (the live [InlineWordEditor] renders it instead).
 */
@Composable
fun TextReplacementPreviewLayer(
    pageWidth: Float,
    pageHeight: Float,
    previewEdits: List<WordPreviewEdit>,
    activeWord: TextWord?,
    fontPxFor: (TextWord) -> Float
) {
    val density = LocalDensity.current
    previewEdits.forEach { edit ->
        if (edit.word == activeWord) return@forEach
        val w = edit.word
        val rectX = (w.x * pageWidth).toInt()
        val rectY = (w.y * pageHeight).toInt()
        val rectW = (w.width * pageWidth).toInt().coerceAtLeast(6)
        val rectH = (w.height * pageHeight).toInt().coerceAtLeast(8)
        Box(
            modifier = Modifier
                .zIndex(7f)
                .offset { IntOffset(rectX, rectY) }
                .size(with(density) { rectW.toDp() }, with(density) { rectH.toDp() }),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = edit.newText,
                style = TextStyle(
                    color = PreviewTextColor,
                    fontSize = with(density) { fontPxFor(w).toSp() }
                ),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false
            )
        }
    }
}

/**
 * Live INLINE editor: a [BasicTextField] placed exactly over the original word (same box, font
 * size, left alignment, no dialog / popup / detached editor). No background fill is drawn — the
 * original glyphs are already absent because the page is showing the renderer-suppressed edit
 * bitmap, so the field types directly into the empty slot (no cover / patch).
 *
 * IME "Done" (and the ✓ chip) commit via [onDone]; the ✕ chip cancels via [onCancel].
 */
@Composable
fun InlineWordEditor(
    initialText: String,
    fontPx: Float,
    offsetX: Int,
    offsetY: Int,
    width: Int,
    height: Int,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember(offsetX, offsetY, initialText) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val focusRequester = remember(offsetX, offsetY) { FocusRequester() }
    LaunchedEffect(offsetX, offsetY) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    // The field, exactly over the word (no background — the slot is already empty on the edit bitmap).
    // Baseline alignment (D1): the glyphs must rest on the PDF baseline, not float in the line box.
    // includeFontPadding=false strips the extra asc/descent leading, and BottomStart drops the single
    // line to the box bottom (≈ the PDF baseline), so it sits where the original glyphs were.
    Box(
        modifier = Modifier
            .zIndex(9f)
            .offset { IntOffset(offsetX, offsetY) }
            .size(with(density) { width.coerceAtLeast(24).toDp() }, with(density) { height.coerceAtLeast(16).toDp() }),
        contentAlignment = Alignment.BottomStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(
                color = PreviewTextColor,
                fontSize = with(density) { fontPx.toSp() },
                lineHeight = with(density) { fontPx.toSp() },
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            cursorBrush = SolidColor(CursorColor),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone(value.text) })
        )
    }

    // Tiny Done / Cancel chips, just above the field (still at the word, not a detached dialog).
    val chipY = (offsetY - 34).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .zIndex(10f)
            .offset { IntOffset(offsetX, chipY) }
    ) {
        EditChip(symbol = "✓", bg = Color(0xFF2196F3)) { keyboard?.hide(); onDone(value.text) }
        Box(Modifier.size(6.dp))
        EditChip(symbol = "✕", bg = Color(0xFF757575)) { keyboard?.hide(); onCancel() }
    }
}

@Composable
private fun EditChip(symbol: String, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(bg, CircleShape)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = Color.White, style = TextStyle(fontSize = 14.sp))
    }
}
