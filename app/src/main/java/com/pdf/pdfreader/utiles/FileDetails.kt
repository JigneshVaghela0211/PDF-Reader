package com.pdf.pdfreader.utiles

data class FileDetails(
    val filePath: String,
    val fileName: String,
    val lastModified: String,
    val size: Long
)