// File: app/src/main/kotlin/io/github/skm0078/audora/di/NotesModule.kt
// Purpose: Provides bookmarks, settings, fixes, highlights and voice notes (items 29, 4, 1, 30, 32) from the database.

package io.github.skm0078.audora.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.notes.VoiceNoteStore
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

    @Provides
    @Singleton
    fun highlightStore(storage: AudoraStorage): HighlightStore = storage.highlights

    @Provides
    @Singleton
    fun voiceNoteStore(storage: AudoraStorage): VoiceNoteStore = storage.voiceNotes
}
