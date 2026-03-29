package com.pdf.pdfreader.data.local

data class SearchResult(
    val fileName: String,
    val pdfPath: String,
    val pageIndex: Int,
    val snippet: String
)
