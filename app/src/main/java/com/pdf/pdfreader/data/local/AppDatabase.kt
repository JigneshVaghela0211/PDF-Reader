package com.pdf.pdfreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PdfEntity::class, PdfTextSnippet::class, BookmarkEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
    abstract fun bookmarkDao(): BookmarkDao
}
