// File: app/src/main/kotlin/io/github/skm0078/audora/di/ListeningStatsModule.kt
// Purpose: One listening log per process (CF-8), shared by the playback service that adds to it and the stats sheet.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.stats.ListeningDaysStore
import io.github.skm0078.audora.core.stats.ListeningLog
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ListeningStatsModule {
    /** Under filesDir, by the name the backup lists, so stats move with a backup to a new phone. */
    @Provides
    @Singleton
    fun listeningLog(@ApplicationContext context: Context): ListeningLog =
        ListeningLog(ListeningDaysStore(File(context.filesDir, ListeningDaysStore.FILE_NAME)))
}

/** For the library's stats sheet, which is not an injected class. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ListeningStatsEntryPoint {
    fun listeningLog(): ListeningLog

    companion object {
        fun of(context: Context): ListeningStatsEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ListeningStatsEntryPoint::class.java)
    }
}
