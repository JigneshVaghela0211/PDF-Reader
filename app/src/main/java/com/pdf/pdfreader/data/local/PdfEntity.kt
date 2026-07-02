package com.pdf.pdfreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_files")
data class PdfEntity(
    @PrimaryKey val path: String,
    val name: String,
    val lastModified: Long,
    val size: Long,
    val type: String,
    val formattedSize: String,
    val formattedDate: String,
    val isLocked: Boolean,
    val isTrashed: Boolean,
    val isFavorite: Boolean = false,
    val lastOpened: Long = 0L,
    val lastOpenedPage: Int = 0,
    val thumbnailPath: String? = null,
    /** Comma-separated user labels/tags. Empty string = none. */
    val tags: String = ""
)
