// File: app/src/main/kotlin/io/github/skm0078/audora/di/FolderSyncModule.kt
// Purpose: Provides folder sync (CF-16) over the library's stores, and hands it to screens Hilt does not build.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.playback.sync.FolderSync
import io.github.skm0078.audora.playback.sync.SyncStores
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FolderSyncModule {
    @Provides
    @Singleton
    @Suppress("LongParameterList") // The stores a sync reads and changes, each once.
    fun folderSync(
        @ApplicationContext context: Context,
        library: Library,
        anchors: ResumeAnchorStore,
        bookmarks: BookmarkStore,
        highlights: HighlightStore,
        documents: DocumentAccess,
    ): FolderSync = FolderSync(context, SyncStores(library, anchors, bookmarks, highlights, documents))
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FolderSyncEntryPoint {
    fun folderSync(): FolderSync

    companion object {
        fun of(context: Context): FolderSync =
            EntryPointAccessors.fromApplication(context.applicationContext, FolderSyncEntryPoint::class.java)
                .folderSync()
    }
}
