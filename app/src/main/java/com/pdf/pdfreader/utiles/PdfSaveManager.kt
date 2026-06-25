package com.pdf.pdfreader.utiles

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * Centralises saving an edited [PDDocument]. Writes to a sibling `<name>_edited.pdf` so the
 * original file is never modified, and returns the output path.
 */
class PdfSaveManager {

    /** Save [document] next to [originalPath] as `<name>_edited.pdf`; returns the new path. */
    fun save(document: PDDocument, originalPath: String): String {
        val outputPath = outputPathFor(originalPath)
        document.save(File(outputPath))
        return outputPath
    }

    fun outputPathFor(originalPath: String): String {
        val file = File(originalPath)
        val name = file.nameWithoutExtension
        val parent = file.parentFile?.absolutePath ?: file.absolutePath
        return "$parent/${name}_edited.pdf"
    }
}
