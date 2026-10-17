// File: app/src/main/kotlin/io/github/skm0078/audora/di/ReaderModule.kt
// Purpose: Provides the reader's config, saved settings and PDF opener as app singletons (P1-05, P4-03).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.ui.reader.DataStoreReaderPreferences
import io.github.skm0078.audora.ui.reader.PdfReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.reader.ViewPreference
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {
    @Provides
    @Singleton
    fun readerConfig(): ReaderConfig = ReaderConfig()

    @Provides
    @Singleton
    fun readerPreferences(@ApplicationContext context: Context): DataStoreReaderPreferences =
        DataStoreReaderPreferences(context)

    @Provides
    fun speedPreference(preferences: DataStoreReaderPreferences): SpeedPreference = preferences

    @Provides
    fun viewPreference(preferences: DataStoreReaderPreferences): ViewPreference = preferences

    @Provides
    fun screenPreference(preferences: DataStoreReaderPreferences): ScreenPreference = preferences

    @Provides
    @Singleton
    fun documentOpener(@ApplicationContext context: Context): ReaderDocumentOpener =
        PdfReaderDocumentOpener(context, Dispatchers.IO)
}
