package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/** Outcome of turning a scanned PDF into a searchable one (invisible text layer). */
data class MakeSearchableResult(
    val outputPath: String,
    val pagesProcessed: Int,
    val wordsAdded: Int
)
