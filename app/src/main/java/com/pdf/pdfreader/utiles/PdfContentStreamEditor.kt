package com.pdf.pdfreader.utiles

import android.util.Log
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream

/**
 * Low-level content-stream surgery: replaces or empties the operands of specific
 * text-showing operators on a page, then writes the modified stream back.
 *
 * The page's content stream is parsed into tokens ([PDFStreamParser] transparently inflates
 * any compression), the targeted operands are rewritten, and the result is re-serialised with
 * FLATE compression via [ContentStreamWriter] + [PDPage.setContents].
 *
 * Show-operation indices match [PdfTextObjectDetector]: one per `Tj`/`'`/`"`, one per
 * `COSString` segment inside a `TJ` array.
 */
class PdfContentStreamEditor {

    companion object {
        private const val TAG = "PdfContentStreamEditor"
        private val EMPTY = COSString(ByteArray(0))
    }

    /**
     * Rewrite the operands of the show-text ops named in [replacements].
     *
     * - value `COSString` → set the op's text operand to those bytes
     * - value `null`      → empty the op's text operand (removes that text)
     *
     * Ops not present in the map are left untouched. Returns true if the stream was changed.
     */
    fun rewriteShowOps(
        document: PDDocument,
        page: PDPage,
        replacements: Map<Int, COSString?>
    ): Boolean {
        if (replacements.isEmpty() || page.contents == null) return false

        return try {
            val parser = PDFStreamParser(page)
            parser.parse()
            val tokens = parser.tokens

            val newTokens = ArrayList<Any>(tokens.size)
            var showIndex = 0
            var changed = false

            for (token in tokens) {
                newTokens.add(token)
                if (token !is Operator) continue

                when (token.name) {
                    "Tj", "'", "\"" -> {
                        if (replacements.containsKey(showIndex)) {
                            val pos = findOperand(newTokens) { it is COSString }
                            if (pos >= 0) {
                                newTokens[pos] = replacements[showIndex] ?: EMPTY
                                changed = true
                            }
                        }
                        showIndex++
                    }
                    "TJ" -> {
                        val arrPos = findOperand(newTokens) { it is COSArray }
                        if (arrPos >= 0) {
                            val arr = newTokens[arrPos] as COSArray
                            val rebuilt = COSArray()
                            for (el in arr) {
                                if (el is COSString) {
                                    if (replacements.containsKey(showIndex)) {
                                        rebuilt.add(replacements[showIndex] ?: EMPTY)
                                        changed = true
                                    } else {
                                        rebuilt.add(el)
                                    }
                                    showIndex++
                                } else {
                                    rebuilt.add(el)
                                }
                            }
                            newTokens[arrPos] = rebuilt
                        }
                    }
                }
            }

            if (!changed) return false

            val newStream = PDStream(document)
            newStream.createOutputStream(COSName.FLATE_DECODE).use { out ->
                ContentStreamWriter(out).writeTokens(newTokens)
            }
            page.setContents(newStream)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Content-stream rewrite failed; page left unchanged", e)
            false
        }
    }

    /** Walk backward from the just-added operator to its operand token. */
    private inline fun findOperand(tokens: List<Any>, predicate: (Any) -> Boolean): Int {
        for (i in tokens.size - 2 downTo 0) {
            val t = tokens[i]
            if (t is Operator) return -1 // crossed into a previous instruction
            if (predicate(t)) return i
        }
        return -1
    }
}
