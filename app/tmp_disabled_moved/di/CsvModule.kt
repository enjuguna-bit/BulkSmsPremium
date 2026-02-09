package com.bulksms.smsmanager.di

import android.content.ContentResolver
import android.content.Context
import com.bulksms.smsmanager.data.csv.CsvFileHandler
import com.bulksms.smsmanager.data.csv.CsvPreviewService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for CSV upload dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object CsvModule {
    
    @Singleton
    @Provides
    fun provideCsvFileHandler(
        @ApplicationContext context: Context
    ): CsvFileHandler {
        return CsvFileHandler(context.contentResolver)
    }
    
    @Singleton
    @Provides
    fun provideCsvPreviewService(): CsvPreviewService {
        return CsvPreviewService()
    }
}
