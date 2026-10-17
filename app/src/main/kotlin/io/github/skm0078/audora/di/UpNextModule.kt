// File: app/src/main/kotlin/io/github/skm0078/audora/di/UpNextModule.kt
// Purpose: Binds the Up next queue (plan item 25); in memory until the playback service backs it.

package io.github.skm0078.audora.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.ui.upnext.InMemoryUpNext
import io.github.skm0078.audora.ui.upnext.UpNextPort
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpNextModule {
    // TODO(item-25): swap for the service-backed queue (Room table, advance at the end of a book).
    @Provides
    @Singleton
    fun upNext(): UpNextPort = InMemoryUpNext()
}
