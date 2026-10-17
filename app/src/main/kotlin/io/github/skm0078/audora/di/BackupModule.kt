// File: app/src/main/kotlin/io/github/skm0078/audora/di/BackupModule.kt
// Purpose: Provides back up and restore as one app singleton, so a running job outlives the screen (item 28).

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.storage.AudoraStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun backups(@ApplicationContext context: Context, storage: AudoraStorage): Backups = Backups(context, storage)
}
