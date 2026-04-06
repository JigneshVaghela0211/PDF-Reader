package com.pdf.pdfreader.utiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.pdf.pdfreader.domain.model.EditedTextBlock
import com.pdf.pdfreader.domain.model.ImageElement
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
 * Exports the edited PDF by merging text and image overlays onto the original PDF.
 *
 * Strategy:
 * - Text edits: Draw white rectangle over original text area, then draw new text on top
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
        viewWidth: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val originalFile = File(originalPath)
            if (!originalFile.exists()) {
                Log.e(TAG, "Original file not found: $originalPath")
                return@withContext null
            }

            // Generate output path
            val outputPath = generateOutputPath(originalPath)
            Log.d(TAG, "Exporting to: $outputPath")

            PDDocument.load(originalFile).use { document ->
                // Group edits by page index
                val textEditsByPage = editedTextBlocks.groupBy { it.originalBlock.pageIndex }
                val imagesByPage = imageElements.groupBy { it.pageIndex }

                val allPages = (textEditsByPage.keys + imagesByPage.keys).distinct().sorted()

                for (pageIdx in allPages) {
                    coroutineContext.ensureActive()

                    if (pageIdx < 0 || pageIdx >= document.numberOfPages) continue

                    val page = document.getPage(pageIdx)
                    val cropBox = page.cropBox
                    val pdfWidth = cropBox.width
                    val pdfHeight = cropBox.height
                    val scaleX = pdfWidth / viewWidth.toFloat()

                    PDPageContentStream(
                        document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        // ─── Process Text Edits ─────────────────────────
                        textEditsByPage[pageIdx]?.forEach { editedBlock ->
                            coroutineContext.ensureActive()
                            drawTextEdit(cs, editedBlock, pdfWidth, pdfHeight)
                        }

                        // ─── Process Image Inserts ──────────────────────
                        imagesByPage[pageIdx]?.forEach { imageElement ->
                            coroutineContext.ensureActive()
                            drawImage(context, document, cs, imageElement, pdfWidth, pdfHeight, scaleX)
                        }
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
     * Draw a text edit onto the PDF page:
     * 1. Draw white rectangle to hide original text
     * 2. Draw new text on top
     */
    private fun drawTextEdit(
        cs: PDPageContentStream,
        editedBlock: EditedTextBlock,
        pdfWidth: Float,
        pdfHeight: Float
    ) {
        val block = editedBlock.originalBlock

        // Calculate PDF coordinates (PDF origin is bottom-left)
        val rectX = block.x * pdfWidth
        val rectY = pdfHeight - (block.y * pdfHeight) - (block.height * pdfHeight)
        val rectW = block.width * pdfWidth
        val rectH = block.height * pdfHeight

        // Step 1: Draw white rectangle to cover original text
        cs.saveGraphicsState()
        cs.setNonStrokingColor(255, 255, 255)
        cs.addRect(rectX - 1f, rectY - 1f, rectW + 2f, rectH + 2f)
        cs.fill()
        cs.restoreGraphicsState()

        // Step 2: Draw new text
        val color = editedBlock.newColor
        val fontSize = editedBlock.newFontSize

        cs.beginText()
        cs.setNonStrokingColor(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )

        // Use closest available standard font
        val font = PDType1Font.HELVETICA
        cs.setFont(font, fontSize)

        // Position text at the top-left of the original block area
        // Adjust for baseline (font ascent)
        val textY = rectY + rectH - fontSize
        cs.newLineAtOffset(rectX, textY)

        // Handle multi-line text
        val lines = editedBlock.newText.split("\n")
        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) {
                cs.newLineAtOffset(0f, -fontSize * 1.2f)
            }
            try {
                cs.showText(line)
            } catch (e: Exception) {
                // Some characters might not be supported by Type1 font
                Log.w(TAG, "Failed to render text line: '$line'", e)
                val sanitized = line.replace(Regex("[^\\x20-\\x7E]"), "?")
                try { cs.showText(sanitized) } catch (_: Exception) {}
            }
        }
        cs.endText()
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
            val bitmap = inputStream.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return

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

    /**
     * Generate output file path: `<name>_edited.pdf`
     */
    private fun generateOutputPath(originalPath: String): String {
        val file = File(originalPath)
        val name = file.nameWithoutExtension
        val parent = file.parentFile?.absolutePath ?: file.absolutePath
        return "$parent/${name}_edited.pdf"
    }
}
