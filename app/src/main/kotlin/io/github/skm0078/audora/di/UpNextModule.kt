// File: app/src/main/kotlin/io/github/skm0078/audora/di/UpNextModule.kt
// Purpose: Provides the Up next queue (plan item 25): one saved queue shared by the screens and the playback service.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.progress.TimeLeft
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.playback.upnext.UpNextStore
import io.github.skm0078.audora.ui.reader.SpeedPreference
import io.github.skm0078.audora.ui.upnext.StoredUpNext
import io.github.skm0078.audora.ui.upnext.UpNextPort
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object UpNextModule {
    // App-wide: queue saves and the rows outlive any one screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun upNextStore(@ApplicationContext context: Context): UpNextStore =
        UpNextStore.forContext(context, scope, Dispatchers.IO)

    @Provides
    @Singleton
    fun upNext(
        store: UpNextStore,
        library: Library,
        anchors: ResumeAnchorStore,
        speeds: SpeedPreference,
        config: ReaderConfig,
    ): UpNextPort = StoredUpNext(
        store = store,
        library = library,
        anchors = anchors.observeAll(),
        scope = scope,
        timeLeft = TimeLeft(config.timeLeft, config.speech),
        speed = { speeds.load() ?: SpeechStyle.NORMAL_RATE },
    )
}
