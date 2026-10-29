// File: app/src/main/kotlin/io/github/skm0078/audora/di/HomeModule.kt
// Purpose: One set of book glances per process (ADR 2026-10-28-make-it-yours), shared by the playback service that
//          records the last line heard, the reader that estimates the time left and the library that shows both.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.home.BookGlanceStore
import io.github.skm0078.audora.core.home.BookGlances
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {
    @Provides
    @Singleton
    fun bookGlances(@ApplicationContext context: Context): BookGlances =
        BookGlances(BookGlanceStore(File(context.filesDir, BookGlanceStore.FILE_NAME)), System::currentTimeMillis)
}

/** For the library and the reader, which are not injected classes. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeEntryPoint {
    fun bookGlances(): BookGlances

    companion object {
        fun of(context: Context): HomeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, HomeEntryPoint::class.java)
    }
}
