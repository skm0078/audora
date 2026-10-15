// File: app/src/main/kotlin/io/github/skm0078/audora/di/ResumeModule.kt
// Purpose: Provides resume anchors (stored in the app database) and the locator every resume path shares (P1-06).

package io.github.skm0078.audora.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.storage.AudoraStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ResumeModule {
    @Provides
    @Singleton
    fun resumeAnchorStore(storage: AudoraStorage): ResumeAnchorStore = storage.resumeAnchors

    @Provides
    @Singleton
    fun resumeLocator(store: ResumeAnchorStore): ResumeLocator = ResumeLocator(store, PIPELINE_VERSION)
}
