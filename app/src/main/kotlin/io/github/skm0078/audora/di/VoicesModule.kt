// File: app/src/main/kotlin/io/github/skm0078/audora/di/VoicesModule.kt
// Purpose: Provides the voice manager (P4-01) and voice licences (P3-06) to the UI, over the player's VoiceLibrary.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.ui.licences.LibraryLicenceSource
import io.github.skm0078.audora.ui.licences.LicenceSource
import io.github.skm0078.audora.ui.voices.LibraryVoiceManager
import io.github.skm0078.audora.ui.voices.VoiceManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoicesModule {
    @Provides
    @Singleton
    fun voiceManager(@ApplicationContext context: Context): VoiceManager =
        LibraryVoiceManager(VoiceLibrary.get(context))

    @Provides
    @Singleton
    fun licenceSource(@ApplicationContext context: Context): LicenceSource =
        LibraryLicenceSource(VoiceLibrary.get(context), context.assets)
}
