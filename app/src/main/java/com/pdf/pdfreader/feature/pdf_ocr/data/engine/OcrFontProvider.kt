package com.pdf.pdfreader.feature.pdf_ocr.data.engine

import android.util.Log
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import com.tom_roush.fontbox.ttf.TrueTypeCollection
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileInputStream
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks a PDF font that can encode OCR text:
 *
 * - WinAnsi-encodable text → Standard-14 HELVETICA (zero size cost).
 * - Anything else → a **system Noto font** embedded as a subsetted [PDType0Font]
 *   (only used glyphs land in the output). No fonts are bundled in the APK —
 *   Android ships Noto for every supported script.
 * - When no usable system font loads (e.g. CFF-flavored CJK collections, which
 *   PDFBox 2.x cannot embed), returns null; callers fall back to HELVETICA +
 *   `?`-sanitization, matching the previous behavior.
 *
 * Loaded fonts are cached per document (embedding registers the font IN the
 * document, so they must not leak across documents).
 */
@Singleton
class OcrFontProvider @Inject constructor() {

    companion object {
        private const val TAG = "OcrFontProvider"
        private const val FONTS_DIR = "/system/fonts"

        /** TTF candidates per script, tried in order. */
        private val TTF_CANDIDATES = mapOf(
            OcrScript.LATIN to listOf("Roboto-Regular.ttf"),
            OcrScript.DEVANAGARI to listOf(
                "NotoSansDevanagari-Regular.ttf",
                "NotoSansDevanagari-VF.ttf",
                "NotoSerifDevanagari-VF.ttf"
            )
        )

        /** TTC collections + subfont name per CJK script. */
        private val TTC_CANDIDATES = mapOf(
            OcrScript.CHINESE to listOf(
                "NotoSansCJK-Regular.ttc" to "NotoSansCJKsc-Regular",
                "NotoSerifCJK-Regular.ttc" to "NotoSerifCJKsc-Regular"
            ),
            OcrScript.JAPANESE to listOf(
                "NotoSansCJK-Regular.ttc" to "NotoSansCJKjp-Regular",
                "NotoSerifCJK-Regular.ttc" to "NotoSerifCJKjp-Regular"
            ),
            OcrScript.KOREAN to listOf(
                "NotoSansCJK-Regular.ttc" to "NotoSansCJKkr-Regular",
                "NotoSerifCJK-Regular.ttc" to "NotoSerifCJKkr-Regular"
            )
        )
    }

    /** Per-document font cache: script → loaded font (null = load already failed). */
    private val cache = WeakHashMap<PDDocument, MutableMap<OcrScript, PDFont?>>()

    /**
     * A font able to encode [text], or null when only sanitized HELVETICA output
     * is possible. HELVETICA itself is returned for plain WinAnsi text.
     */
    fun fontFor(doc: PDDocument, script: OcrScript, text: String): PDFont? {
        if (isWinAnsiEncodable(text)) return PDType1Font.HELVETICA
        val embedded = embeddedFontFor(doc, script)
        // A loaded font can still miss specific glyphs — verify it encodes this text.
        return embedded?.takeIf { canEncode(it, text) }
    }

    private fun isWinAnsiEncodable(text: String): Boolean = canEncode(PDType1Font.HELVETICA, text)

    private fun canEncode(font: PDFont, text: String): Boolean = try {
        font.encode(text)
        true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun embeddedFontFor(doc: PDDocument, script: OcrScript): PDFont? {
        val docCache = cache.getOrPut(doc) { mutableMapOf() }
        if (docCache.containsKey(script)) return docCache[script]
        val font = loadSystemFont(doc, script)
        docCache[script] = font
        return font
    }

    private fun loadSystemFont(doc: PDDocument, script: OcrScript): PDFont? {
        TTF_CANDIDATES[script].orEmpty().forEach { name ->
            val file = File(FONTS_DIR, name)
            if (!file.exists()) return@forEach
            try {
                val font = PDType0Font.load(doc, FileInputStream(file), /* embedSubset = */ true)
                Log.d(TAG, "Loaded system font $name for $script")
                return font
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $name for $script", e)
            }
        }
        TTC_CANDIDATES[script].orEmpty().forEach { (ttcName, subFont) ->
            val file = File(FONTS_DIR, ttcName)
            if (!file.exists()) return@forEach
            try {
                val ttf = TrueTypeCollection(file).getFontByName(subFont) ?: return@forEach
                val font = PDType0Font.load(doc, ttf, /* embedSubset = */ true)
                Log.d(TAG, "Loaded $subFont from $ttcName for $script")
                return font
            } catch (e: Exception) {
                // Expected on devices whose Noto CJK collection is CFF-flavored:
                // PDFBox 2.x can only embed TrueType (glyf) outlines.
                Log.w(TAG, "Failed to load $subFont from $ttcName for $script", e)
            }
        }
        Log.w(TAG, "No usable system font for $script — falling back to sanitized HELVETICA")
        return null
    }
}
