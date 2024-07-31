package com.pdf.pdfreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationComponent {


    @Provides
    @Singleton
    internal fun provideApplicationContext(@ApplicationContext applicationContext: Context): Context {
        return applicationContext
    }


}