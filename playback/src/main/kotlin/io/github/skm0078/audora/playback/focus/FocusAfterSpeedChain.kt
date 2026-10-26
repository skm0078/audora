// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusAfterSpeedChain.kt
// Purpose: Media3's default audio chain with the focus sound mixed in last, after the speed and pitch change (Q-1).

package io.github.skm0078.audora.playback.focus

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Media3's default chain is "your processors, then silence skipping, then Sonic (speed and pitch)". The focus sound
 * used to be one of "your processors", which was fine for noise made on the phone but stretches and pitches a
 * recording along with the voice. This chain is the default one with [focus] moved to the end. Speed, pitch and
 * silence skipping still go to the default chain, which owns those processors.
 *
 * The only difference in timing: the sound's short fade at the book's very end ([FocusMixer.tail]) comes after
 * Sonic, so the player's position over those last few hundred milliseconds is not scaled by the speed. Nothing reads
 * it there.
 */
@OptIn(UnstableApi::class)
class FocusAfterSpeedChain(focus: AudioProcessor) : AudioProcessorChain {
    private val defaults = DefaultAudioSink.DefaultAudioProcessorChain()
    private val processors: Array<AudioProcessor> = defaults.audioProcessors + focus

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        defaults.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
        defaults.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long): Long = defaults.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = defaults.skippedOutputFrameCount
}
