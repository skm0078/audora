// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusSoundProcessor.kt
// Purpose: The Media3 audio processor that runs FocusMixer on the voice's PCM on its way to the speaker (plan item 72).

package io.github.skm0078.audora.playback.focus

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.skm0078.audora.core.config.FocusSoundConfig
import io.github.skm0078.audora.core.model.FocusSoundOptions
import java.nio.ByteBuffer

/**
 * Active for 16-bit PCM (what every voice renders); anything else passes through untouched. [options] is read once
 * per buffer on the playback thread, so a change is heard within one buffer. With the sound off the mixer copies
 * the voice as it is.
 */
@OptIn(UnstableApi::class)
class FocusSoundProcessor(
    private val options: () -> FocusSoundOptions,
    private val config: FocusSoundConfig = FocusSoundConfig(),
) : BaseAudioProcessor() {
    private var mixer: FocusMixer? = null
    private var samples = ShortArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val count = bytes / Short.SIZE_BYTES
        if (samples.size < count) samples = ShortArray(count)
        for (i in 0 until count) samples[i] = inputBuffer.getShort()
        mixerFor(inputAudioFormat.sampleRate).mix(samples, count, inputAudioFormat.channelCount, options())
        val output = replaceOutputBuffer(count * Short.SIZE_BYTES)
        for (i in 0 until count) output.putShort(samples[i])
        output.flip()
    }

    override fun onReset() {
        mixer = null
        samples = ShortArray(0)
    }

    /** One mixer per sample rate; a new voice at another rate starts the sound again (with its fade-in). */
    private fun mixerFor(sampleRate: Int): FocusMixer =
        mixer?.takeIf { it.sampleRate == sampleRate } ?: FocusMixer(sampleRate, config).also { mixer = it }
}
