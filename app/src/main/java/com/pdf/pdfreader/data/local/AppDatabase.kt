package com.pdf.pdfreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PdfEntity::class, PdfTextSnippet::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
}
