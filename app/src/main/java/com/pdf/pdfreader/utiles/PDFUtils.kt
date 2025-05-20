package com.pdf.pdfreader.utiles


import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File


object PDFUtils {

    fun hasPassword(path: String): Boolean {
        return PDDocument.load(File(path)).isEncrypted
    }
}