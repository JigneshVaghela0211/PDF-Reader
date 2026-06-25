package com.pdf.pdfreader.selection.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.pdf.pdfreader.selection.model.PdfSelectableWord
import javax.inject.Inject

/**
 * Turns a word-level selection into clipboard text and copies it. Words are in reading order, so a
 * downward jump in y marks a new visual line; everything else is space-joined.
 */
class PdfClipboardManager @Inject constructor() {

    /** Join selected [words] into display text, preserving spaces and visual line breaks. */
    fun selectedText(words: List<PdfSelectableWord>): String {
        val sb = StringBuilder()
        var prev: PdfSelectableWord? = null
        for (w in words) {
            prev?.let { sb.append(if (w.y - it.y > it.height * 0.6f) "\n" else " ") }
            sb.append(w.text)
            prev = w
        }
        return sb.toString()
    }

    /** Copy [words] to the system clipboard. Returns false if there was nothing to copy. */
    fun copy(context: Context, words: List<PdfSelectableWord>): Boolean {
        if (words.isEmpty()) return false
        val text = selectedText(words)
        if (text.isBlank()) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PDF Text", text))
        return true
    }
}
