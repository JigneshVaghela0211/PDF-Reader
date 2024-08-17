package com.pdf.pdfreader.utiles

data class PdfFileDetails(
    val filePath: String,
    val fileName: String,
    val lastModified: Long,
    val size: Long
)
