package com.pdf.pdfreader.domain.model

data class PdfFile(
    val path: String,
    val name: String,
    val lastModified: Long,
    val size: Long,
    val type: String,
    val formattedSize: String,
    val formattedDate: String,
    val isLocked: Boolean = false,
    val isTrashed: Boolean = false,
    val isFavorite: Boolean = false,
    val lastOpened: Long = 0L,
    val lastOpenedPage: Int = 0,
    val thumbnailPath: String? = null
)
