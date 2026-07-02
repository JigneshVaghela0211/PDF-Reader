package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.ImageElement
import com.pdf.pdfreader.domain.model.PdfAnnotation
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Exports the edited PDF by applying text edits and image inserts to the original PDF.
 *
 * Strategy:
 * - Text edits: real content-stream modification via [PdfTextReplacementEngine] — the actual
 *   PDF text object is replaced (no white box, no overlay). See that class for the tiered
 *   font-preservation strategy.
 * - Image inserts: Convert URI → bitmap → PDImageXObject, draw at specified position with rotation
 * - Saves as a NEW file: `<original_name>_edited.pdf` (never modifies the original)
 * - Memory-safe: Processes one page at a time, recycles bitmaps immediately
 */
@Singleton
class PdfExportManager @Inject constructor() {

    companion object {
        private const val TAG = "PdfExportManager"
        private const val MAX_IMAGE_DIMENSION = 2048
    }

    private val textReplacementEngine = PdfTextReplacementEngine()
    private val saveManager = PdfSaveManager()

    /**
     * Export the edited PDF.
     *
     * @param context Application context for URI resolution
     * @param originalPath Path to the original PDF file
     * @param editedTextBlocks All text edits across all pages
     * @param imageElements All inserted images across all pages
     * @param viewWidth Width of the view in pixels (for coordinate conversion)
     * @return Path to the saved edited PDF, or null on failure
     */
    suspend fun exportEditedPdf(
        context: Context,
        originalPath: String,
        editedTextBlocks: List<EditedTextBlock>,
        imageElements: List<ImageElement>,
        annotations: List<PdfAnnotation> = emptyList(),
        viewWidth: Int
    ): String? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        try {
            val originalFile = File(originalPath)
            if (!originalFile.exists()) {
                Log.e(TAG, "Original file not found: $originalPath")
                return@withContext null
            }

            val outputPath = saveManager.outputPathFor(originalPath)
            Log.d(TAG, "Exporting to: $outputPath")

            PDDocument.load(originalFile).use { document ->
                // Group edits by page index
                val textEditsByPage = editedTextBlocks.groupBy { it.originalBlock.pageIndex }
                val imagesByPage = imageElements.groupBy { it.pageIndex }
                val annotationsByPage = annotations.groupBy { it.pageIndex }

                val allPages = (textEditsByPage.keys + imagesByPage.keys + annotationsByPage.keys)
                    .distinct().sorted()

                for (pageIdx in allPages) {
                    coroutineContext.ensureActive()

                    if (pageIdx < 0 || pageIdx >= document.numberOfPages) continue

                    val page = document.getPage(pageIdx)
                    val cropBox = page.cropBox
                    val pdfWidth = cropBox.width
                    val pdfHeight = cropBox.height
                    val scaleX = pdfWidth / viewWidth.toFloat()

                    // ─── Real text replacement (modifies the content stream) ──
                    // Must run before opening the APPEND stream for images, since
                    // replacing text calls page.setContents(), which would otherwise
                    // drop any appended image content.
                    textEditsByPage[pageIdx]?.forEach { editedBlock ->
                        coroutineContext.ensureActive()
                        textReplacementEngine.replaceText(document, pageIdx, editedBlock)
                    }

                    // ─── Image inserts + freehand/text annotations (appended overlay) ──
                    val pageImages = imagesByPage[pageIdx].orEmpty()
                    val pageAnns = annotationsByPage[pageIdx].orEmpty()
                    val drawableAnns = pageAnns.filter { it is PdfAnnotation.Path || it is PdfAnnotation.TextNote }
                    if (pageImages.isNotEmpty() || drawableAnns.isNotEmpty()) {
                        PDPageContentStream(
                            document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { cs ->
                            pageImages.forEach { imageElement ->
                                coroutineContext.ensureActive()
                                drawImage(context, document, cs, imageElement, pdfWidth, pdfHeight, scaleX)
                            }
                            drawableAnns.forEach { ann ->
                                coroutineContext.ensureActive()
                                when (ann) {
                                    is PdfAnnotation.Path -> drawPathAnnotation(cs, ann, scaleX, pdfHeight)
                                    is PdfAnnotation.TextNote -> drawTextNoteAnnotation(cs, ann, scaleX, pdfHeight)
                                    else -> Unit
                                }
                            }
                        }
                    }

                    // ─── Text markup (real page-level Highlight/Underline/StrikeOut) ──
                    pageAnns.filterIsInstance<PdfAnnotation.TextMarkup>().forEach { markup ->
                        writeMarkupAnnotation(page, markup, pdfWidth, pdfHeight)
                    }
                }

                // Save to output file
                document.save(File(outputPath))
                Log.d(TAG, "Export successful: $outputPath")
            }

            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            null
        }
    }

    /**
     * Draw an image element onto the PDF page.
     * Converts the URI to a bitmap, creates a PDImageXObject, and draws it
     * at the specified position with rotation.
     */
    private fun drawImage(
        context: Context,
        document: PDDocument,
        cs: PDPageContentStream,
        imageElement: ImageElement,
        pdfWidth: Float,
        pdfHeight: Float,
        scaleX: Float
    ) {
        try {
            val uri = Uri.parse(imageElement.uri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Decode with size limit for memory safety
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val sizeStream = context.contentResolver.openInputStream(uri) ?: return
            BitmapFactory.decodeStream(sizeStream, null, options)
            sizeStream.close()

            val sampleSize = calculateSampleSize(
                options.outWidth, options.outHeight, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val rawBitmap = inputStream.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return

            // Bake any mirror (flip) into the pixels — matches the on-screen graphicsLayer
            // scaleX/scaleY = -1. Returns the same bitmap when no flip is needed.
            val bitmap = applyFlip(rawBitmap, imageElement.flipHorizontal, imageElement.flipVertical)

            try {
                val pdImage = LosslessFactory.createFromImage(document, bitmap)

                // Calculate PDF position (convert from view coords to PDF coords)
                val imgX = imageElement.position.x * scaleX
                val imgWidth = imageElement.width * imageElement.scale * scaleX
                val imgHeight = imageElement.height * imageElement.scale * scaleX
                val imgY = pdfHeight - (imageElement.position.y * scaleX) - imgHeight

                cs.saveGraphicsState()

                // Apply rotation if needed
                if (imageElement.rotation != 0f) {
                    val centerX = imgX + imgWidth / 2f
                    val centerY = imgY + imgHeight / 2f
                    val radians = Math.toRadians(imageElement.rotation.toDouble())
                    val cos = Math.cos(radians).toFloat()
                    val sin = Math.sin(radians).toFloat()

                    // Translate-Rotate-Translate matrix
                    val matrix = android.graphics.Matrix()
                    matrix.postTranslate(-centerX, -centerY)
                    matrix.postRotate(imageElement.rotation)
                    matrix.postTranslate(centerX, centerY)

                    // Apply AffineTransform via content stream
                    val at = com.tom_roush.pdfbox.util.Matrix()
                    at.setValue(0, 0, cos)
                    at.setValue(0, 1, sin)
                    at.setValue(1, 0, -sin)
                    at.setValue(1, 1, cos)
                    at.setValue(2, 0, centerX * (1 - cos) + centerY * sin)
                    at.setValue(2, 1, centerY * (1 - cos) - centerX * sin)
                    cs.transform(at)
                }

                cs.drawImage(pdImage, imgX, imgY, imgWidth, imgHeight)
                cs.restoreGraphicsState()

            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to draw image: ${imageElement.uri}", e)
        }
    }

    /** Draw a freehand path annotation into the page content stream. */
    private fun drawPathAnnotation(
        cs: PDPageContentStream,
        ann: PdfAnnotation.Path,
        scaleX: Float,
        pdfHeight: Float
    ) {
        if (ann.points.size < 2) return
        cs.setStrokingColor((ann.color.red * 255).toInt(), (ann.color.green * 255).toInt(), (ann.color.blue * 255).toInt())
        cs.setLineWidth(ann.strokeWidth * scaleX)
        val gs = PDExtendedGraphicsState()
        gs.strokingAlphaConstant = if (ann.isHighlighter) 0.5f else 1.0f
        cs.setGraphicsStateParameters(gs)
        val s = ann.points.first()
        cs.moveTo(s.x * scaleX, pdfHeight - (s.y * scaleX))
        for (i in 1 until ann.points.size) {
            val p = ann.points[i]
            cs.lineTo(p.x * scaleX, pdfHeight - (p.y * scaleX))
        }
        cs.stroke()
    }

    /** Draw a text-note annotation into the page content stream. */
    private fun drawTextNoteAnnotation(
        cs: PDPageContentStream,
        ann: PdfAnnotation.TextNote,
        scaleX: Float,
        pdfHeight: Float
    ) {
        cs.beginText()
        cs.setNonStrokingColor((ann.color.red * 255).toInt(), (ann.color.green * 255).toInt(), (ann.color.blue * 255).toInt())
        cs.setFont(PDType1Font.HELVETICA, ann.fontSize * scaleX)
        val tx = ann.position.x * scaleX
        val ty = pdfHeight - (ann.position.y * scaleX) - (ann.fontSize * scaleX)
        // Compose rotationZ is clockwise (screen y-down); PDF text rotation is
        // counter-clockwise (y-up), so negate the angle. Pivot at the baseline origin.
        if (ann.rotation != 0f) {
            cs.setTextRotation(Math.toRadians(-ann.rotation.toDouble()), tx.toDouble(), ty.toDouble())
        } else {
            cs.newLineAtOffset(tx, ty)
        }
        cs.showText(ann.text)
        cs.endText()
    }

    /**
     * Write one [PdfAnnotation.TextMarkup] as a real PDF text-markup annotation
     * (Highlight / Underline / StrikeOut). Selection rects are normalized (0..1, top-left
     * origin), converting directly to PDF user-space points (bottom-left origin).
     */
    private fun writeMarkupAnnotation(
        page: PDPage,
        markup: PdfAnnotation.TextMarkup,
        pdfWidth: Float,
        pdfHeight: Float
    ) {
        if (markup.rects.isEmpty()) return

        val subType = when (markup.type) {
            PdfAnnotation.MarkupType.HIGHLIGHT ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
            PdfAnnotation.MarkupType.UNDERLINE ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            PdfAnnotation.MarkupType.STRIKETHROUGH ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
        }
        val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(subType)

        val quads = ArrayList<Float>(markup.rects.size * 8)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (r in markup.rects) {
            val left = r.left * pdfWidth
            val right = r.right * pdfWidth
            val top = pdfHeight - r.top * pdfHeight
            val bottom = pdfHeight - r.bottom * pdfHeight
            quads.add(left); quads.add(top)      // top-left
            quads.add(right); quads.add(top)     // top-right
            quads.add(left); quads.add(bottom)   // bottom-left
            quads.add(right); quads.add(bottom)  // bottom-right
            if (left < minX) minX = left
            if (right > maxX) maxX = right
            if (bottom < minY) minY = bottom
            if (top > maxY) maxY = top
        }
        annotation.setQuadPoints(quads.toFloatArray())

        // Full-opacity RGB; transparency is applied via /CA so the colour stays true.
        annotation.setColor(
            com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                floatArrayOf(markup.color.red, markup.color.green, markup.color.blue),
                com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
            )
        )
        annotation.cosObject.setFloat(com.tom_roush.pdfbox.cos.COSName.CA, markup.color.alpha)
        annotation.setRectangle(
            com.tom_roush.pdfbox.pdmodel.common.PDRectangle(minX, minY, maxX - minX, maxY - minY)
        )
        annotation.setPrinted(true)
        annotation.constructAppearances()
        page.annotations.add(annotation)
    }

    /**
     * Returns a mirrored copy of [src] (recycling [src]) when a flip is requested,
     * or [src] unchanged otherwise.
     */
    private fun applyFlip(
        src: android.graphics.Bitmap,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): android.graphics.Bitmap {
        if (!flipHorizontal && !flipVertical) return src
        val matrix = android.graphics.Matrix().apply {
            postScale(
                if (flipHorizontal) -1f else 1f,
                if (flipVertical) -1f else 1f,
                src.width / 2f,
                src.height / 2f
            )
        }
        val flipped = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (flipped != src) src.recycle()
        return flipped
    }

    /**
     * Calculate optimal sample size for bitmap decoding.
     */
    private fun calculateSampleSize(
        width: Int, height: Int,
        maxWidth: Int, maxHeight: Int
    ): Int {
        var sampleSize = 1
        if (width > maxWidth || height > maxHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while (halfWidth / sampleSize >= maxWidth && halfHeight / sampleSize >= maxHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}
