// File: app/src/main/kotlin/io/github/skm0078/audora/di/StudioModule.kt
// Purpose: Provides backup and photo books as app singletons, so a running job outlives the screen (items 28, 62).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.storage.AudoraStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StudioModule {
    @Provides
    @Singleton
    fun backups(@ApplicationContext context: Context, storage: AudoraStorage): Backups = Backups(context, storage)

    // SharedPdfCopies holds no state of its own; this one writes to the same folder as the activity's.
    @Provides
    @Singleton
    fun photoBooks(@ApplicationContext context: Context): PhotoBooks = PhotoBooks(context, SharedPdfCopies(context))
}
