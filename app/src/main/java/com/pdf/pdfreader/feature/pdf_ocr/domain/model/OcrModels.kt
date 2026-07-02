package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/**
 * OCR result hierarchy: document → pages → blocks → lines → words.
 *
 * All coordinates are normalized 0..1 with a **top-left origin**, relative to the
 * page *as displayed* (i.e. the rendered bitmap, which already has the page's
 * /Rotate applied). OcrCoordinateMapper converts to PDF user space for writing.
 * These models are Gson-serialized into the per-page OCR cache, so keep them
 * plain data (no Android types).
 */
data class OcrWord(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Recognizer confidence 0..1; 1 when the model doesn't report one. */
    val confidence: Float,
    /** Rotation of the text in degrees as reported by the recognizer (0 when unknown). */
    val angle: Float
)

data class OcrLine(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val angle: Float,
    val words: List<OcrWord>
)

data class OcrBlock(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val lines: List<OcrLine>
)

data class OcrPage(
    val pageIndex: Int,
    val script: OcrScript,
    /** Crop-box size in PDF points, *unrotated* (as PDFBox reports it). */
    val pdfPageWidth: Float,
    val pdfPageHeight: Float,
    /** The page's /Rotate entry (0/90/180/270). */
    val pageRotation: Int,
    /** Size of the bitmap the OCR ran on (display orientation). */
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val blocks: List<OcrBlock>,
    val createdAtMs: Long
) {
    val words: List<OcrWord> get() = blocks.flatMap { b -> b.lines.flatMap { it.words } }
    val wordCount: Int get() = blocks.sumOf { b -> b.lines.sumOf { it.words.size } }
}

data class OcrDocument(
    val path: String,
    val script: OcrScript,
    /** Page index → recognized page. May be sparse (only OCR'd pages). */
    val pages: Map<Int, OcrPage>
)
