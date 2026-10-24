// File: app/src/main/kotlin/io/github/skm0078/audora/di/LibraryModule.kt
// Purpose: Provides the app database, Storage Access Framework access, the Library, covers, filling books in from
//          their files and what was made from them, as app singletons.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.library.BookFingerprinter
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.playback.details.FileDetailsReader
import io.github.skm0078.audora.playback.documents.SafDocumentAccess
import io.github.skm0078.audora.playback.outputs.EarlierAudiobooks
import io.github.skm0078.audora.playback.storage.AudoraStorage
import io.github.skm0078.audora.ui.library.BookCovers
import io.github.skm0078.audora.ui.library.BookDetailsFill
import io.github.skm0078.audora.ui.library.CoverImage
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

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

    @Provides
    @Singleton
    fun bookCovers(@ApplicationContext context: Context): BookCovers = BookCovers(context, Dispatchers.IO)

    @Provides
    @Singleton
    fun bookOutputs(storage: AudoraStorage): BookOutputStore = storage.outputs

    @Provides
    @Singleton
    fun bookDetailsFill(
        @ApplicationContext context: Context,
        library: Library,
        covers: BookCovers,
        outputs: BookOutputStore,
    ): BookDetailsFill {
        val reader = FileDetailsReader(context)
        val earlier = EarlierAudiobooks(context, outputs)
        return BookDetailsFill(
            library,
            read = reader::read,
            keepCover = { id, image -> covers.keep(id, CoverImage.of(image)) },
            once = { books -> earlier.findOnce(books.map { it.id }) },
        )
    }
}
