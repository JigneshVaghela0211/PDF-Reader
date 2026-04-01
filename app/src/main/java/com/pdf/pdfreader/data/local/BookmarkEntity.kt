package com.pdf.pdfreader.data.local

import androidx.room.*

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = PdfEntity::class,
            parentColumns = ["path"],
            childColumns = ["pdfPath"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pdfPath"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfPath: String,
    val pageIndex: Int,
    val label: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
