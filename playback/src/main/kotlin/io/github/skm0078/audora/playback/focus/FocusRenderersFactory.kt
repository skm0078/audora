// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusRenderersFactory.kt
// Purpose: Media3's default renderers with the focus sound processor (plan item 72) last in the audio sink's chain.

package io.github.skm0078.audora.playback.focus

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * The focus sound is mixed after the speed and pitch change ([FocusAfterSpeedChain]), so a recorded rain keeps its
 * own speed and pitch whatever the voice does. Everything else is Media3's default sink.
 */
@OptIn(UnstableApi::class)
class FocusRenderersFactory(context: Context, private val processor: FocusSoundProcessor) :
    DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessorChain(FocusAfterSpeedChain(processor))
        .build()
}
