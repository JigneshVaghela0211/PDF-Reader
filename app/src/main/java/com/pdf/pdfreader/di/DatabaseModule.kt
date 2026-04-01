package com.pdf.pdfreader.di

import android.content.Context
import androidx.room.Room
import com.pdf.pdfreader.data.local.AppDatabase
import com.pdf.pdfreader.data.local.PdfDao
import dagger.Module
import dagger.hilt.InstallIn
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pdf_files ADD COLUMN lastOpenedPage INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pdfPath` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `label` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`pdfPath`) REFERENCES `pdf_files`(`path`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_pdfPath` ON `bookmarks` (`pdfPath`)")
            }
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, "pdf_reader_db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePdfDao(database: AppDatabase): PdfDao {
        return database.pdfDao()
    }

    @Provides
    fun provideBookmarkDao(database: AppDatabase): com.pdf.pdfreader.data.local.BookmarkDao {
        return database.bookmarkDao()
    }
}
