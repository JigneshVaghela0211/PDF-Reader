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
    val thumbnailPath: String? = null
)
