package com.pdf.pdfreader.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "pdf_text_search")
data class PdfTextSnippet(
    val pdfPath: String,
    val pageIndex: Int,
    val textContent: String
)
