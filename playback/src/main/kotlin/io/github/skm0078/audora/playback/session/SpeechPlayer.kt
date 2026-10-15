// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SpeechPlayer.kt
// Purpose: Builds the ExoPlayer that plays synthesized sentences; shared by the service and the device tests.

package io.github.skm0078.audora.playback.session

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.wav.WavExtractor
import io.github.skm0078.audora.core.config.QueueConfig

object SpeechPlayer {
    /** Pause for calls and other apps' audio, as a reader app must. */
    private const val HANDLE_AUDIO_FOCUS = true

    /**
     * WAV only, from [TtsDataSource]. Audio focus and becoming-noisy (headphones unplugged) pause playback.
     * @param handleAudioFocus false only in device tests, where nothing should depend on the emulator's focus.
     */
    @OptIn(UnstableApi::class)
    fun build(
        context: Context,
        registry: QueueRegistry,
        config: QueueConfig,
        handleAudioFocus: Boolean = HANDLE_AUDIO_FOCUS,
    ): ExoPlayer {
        val wavOnly = ExtractorsFactory { arrayOf<Extractor>(WavExtractor()) }
        val speech = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(TtsDataSource.Factory(registry), wavOnly))
            .setAudioAttributes(speech, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            // Explicit, because setting a stuck timeout would otherwise switch the default to none (S3 finding 4).
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setStuckBufferingDetectionTimeoutMs(config.stuckBufferingTimeoutMs)
            .build()
    }
}
