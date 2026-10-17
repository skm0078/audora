// File: app/src/main/kotlin/io/github/skm0078/audora/di/NotesModule.kt
// Purpose: Provides bookmarks, per-book settings and pronunciation fixes (plan items 29, 4, 1) from the app database.

package io.github.skm0078.audora.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.playback.storage.AudoraStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotesModule {
    @Provides
    @Singleton
    fun bookmarkStore(storage: AudoraStorage): BookmarkStore = storage.bookmarks

    @Provides
    @Singleton
    fun bookSettingsStore(storage: AudoraStorage): BookSettingsStore = storage.bookSettings

    @Provides
    @Singleton
    fun pronunciationStore(storage: AudoraStorage): PronunciationStore = storage.pronunciations
}
