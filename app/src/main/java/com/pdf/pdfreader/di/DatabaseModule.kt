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
        return Room.databaseBuilder(context, AppDatabase::class.java, "pdf_reader_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePdfDao(database: AppDatabase): PdfDao {
        return database.pdfDao()
    }
}
