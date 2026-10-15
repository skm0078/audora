// File: app/src/main/kotlin/io/github/skm0078/audora/di/ReaderModule.kt
// Purpose: Provides the reader's config, saved speed and PDF opener as app singletons (P1-05).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.ui.reader.DataStoreSpeedPreference
import io.github.skm0078.audora.ui.reader.PdfReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.ReaderDocumentOpener
import io.github.skm0078.audora.ui.reader.SpeedPreference
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
    fun speedPreference(@ApplicationContext context: Context): SpeedPreference = DataStoreSpeedPreference(context)

    @Provides
    @Singleton
    fun documentOpener(@ApplicationContext context: Context): ReaderDocumentOpener =
        PdfReaderDocumentOpener(context, Dispatchers.IO)
}
