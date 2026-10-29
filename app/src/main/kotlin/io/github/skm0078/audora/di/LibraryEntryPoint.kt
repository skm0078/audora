// File: app/src/main/kotlin/io/github/skm0078/audora/di/LibraryEntryPoint.kt
// Purpose: The library's singletons for screens and helpers Hilt does not build (routes, sheets), so a new screen
//          does not need another field on MainActivity (ADR-068).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.CollectionStore
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.ShelfLooks
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.words.LookedUpWords
import io.github.skm0078.audora.ui.library.BookCovers

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryEntryPoint {
    fun library(): Library

    fun outputs(): BookOutputStore

    fun covers(): BookCovers

    fun collections(): CollectionStore

    fun shelfLooks(): ShelfLooks

    fun bookSettings(): BookSettingsStore

    fun lookedUpWords(): LookedUpWords

    companion object {
        fun of(context: Context): LibraryEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, LibraryEntryPoint::class.java)
    }
}
