package com.pdf.pdfreader.utiles

data class PdfFileDetails(
    val filePath: String,
    val fileName: String,
    val lastModified: Long,
    val size: Long, val type: String, val parent: Int, val relativePath: String, val addedDate: Long, val trashed:Int,
)
