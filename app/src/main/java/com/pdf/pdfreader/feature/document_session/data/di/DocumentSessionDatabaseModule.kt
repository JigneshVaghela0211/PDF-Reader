package com.pdf.pdfreader.feature.document_session.data.di

import android.content.Context
import androidx.room.Room
import com.pdf.pdfreader.feature.document_session.data.database.DocumentSessionDao
import com.pdf.pdfreader.feature.document_session.data.database.DocumentSessionDatabase
import com.pdf.pdfreader.feature.document_session.data.database.HistoryCommandDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [DocumentSessionDatabase] and its DAOs (MC11).
 *
 * A dedicated, additive module — it does NOT touch the app's main `di/DatabaseModule` / `AppDatabase`.
 * `fallbackToDestructiveMigration` is safe here because this database only caches editing-session
 * state (no user documents), so a schema bump can simply drop it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DocumentSessionDatabaseModule {

    @Provides
    @Singleton
    fun provideDocumentSessionDatabase(
        @ApplicationContext context: Context
    ): DocumentSessionDatabase =
        Room.databaseBuilder(
            context,
            DocumentSessionDatabase::class.java,
            DocumentSessionDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDocumentSessionDao(db: DocumentSessionDatabase): DocumentSessionDao =
        db.documentSessionDao()

    @Provides
    fun provideHistoryCommandDao(db: DocumentSessionDatabase): HistoryCommandDao =
        db.historyCommandDao()
}
