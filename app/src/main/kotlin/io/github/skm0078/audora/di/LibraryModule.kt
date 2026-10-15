// File: app/src/main/kotlin/io/github/skm0078/audora/di/LibraryModule.kt
// Purpose: Provides the app database, Storage Access Framework access and the Library as app singletons (P1-01).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.library.BookFingerprinter
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.playback.documents.SafDocumentAccess
import io.github.skm0078.audora.playback.storage.AudoraStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun audoraStorage(@ApplicationContext context: Context): AudoraStorage = AudoraStorage.open(context)

    @Provides
    @Singleton
    fun documentAccess(@ApplicationContext context: Context): DocumentAccess = SafDocumentAccess(context)

    @Provides
    @Singleton
    fun library(storage: AudoraStorage, documents: DocumentAccess): Library = Library(
        store = storage.books,
        documents = documents,
        fingerprinter = BookFingerprinter(LibraryConfig()),
        clock = System::currentTimeMillis,
    )
}
