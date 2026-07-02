package com.pdf.pdfreader.di

import android.content.Context
import androidx.room.Room
import com.pdf.pdfreader.data.local.AppDatabase
import com.pdf.pdfreader.data.local.PdfDao
import com.pdf.pdfreader.data.local.AnnotationCommandDao
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
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `annotation_commands` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `pdfPath` TEXT NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `isUndone` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Drop the incorrectly-created table from v6 and recreate cleanly
                database.execSQL("DROP TABLE IF EXISTS `annotation_commands`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `annotation_commands` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `pdfPath` TEXT NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `isUndone` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        // Migration 7→8: Support new command types (EditText, Image commands).
        // No schema change — the annotation_commands table already stores type+payload as JSON.
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No-op: new command types are stored using existing columns (type, payload)
            }
        }
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pdf_files ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, "pdf_reader_db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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

    @Provides
    fun provideAnnotationCommandDao(database: AppDatabase): AnnotationCommandDao {
        return database.annotationCommandDao()
    }
}
