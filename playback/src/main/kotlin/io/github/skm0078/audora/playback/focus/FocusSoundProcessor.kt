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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs after Media3's speed and pitch change ([FocusAfterSpeedChain]), so the player's speed above the voice's cap,
 * a replay's slow-down and the pitch setting (ADR-067) change the voice only: a recorded rain stretched or pitched
 * with it would sound wrong. Active for 16-bit PCM (what every voice renders); anything else passes through
 * untouched. [options] is read once per buffer on the playback thread, so a change is heard within one buffer. With
 * the sound off the mixer copies the voice as it is.
 *
 * Media3 drains and flushes its processors at every media item change (each sentence is one), so neither the end of
 * a stream nor a flush means the book ended or was sought. The session says so instead ([FocusSoundCues]): [fadeIn]
 * after a seek or a new playlist, [endsAfterCurrent] when the item playing is the book's last, so only that end
 * fades out.
 */
@OptIn(UnstableApi::class)
class FocusSoundProcessor(
    private val options: () -> FocusSoundOptions,
    private val config: FocusSoundConfig = FocusSoundConfig(),
    private val beds: FocusBeds = FocusBeds.GENERATED,
) : BaseAudioProcessor() {
    private var mixer: FocusMixer? = null
    private var samples = ShortArray(0)
    private val fadeInNext = AtomicBoolean(false)
    private val lastItem = AtomicBoolean(false)

    /** Any thread: what is queued next comes back from silence (a seek, a new playlist). */
    fun fadeIn() = fadeInNext.set(true)

    /** Any thread: whether the item playing is the book's last, so its end is the book's and the sound fades out. */
    fun endsAfterCurrent(last: Boolean) = lastItem.set(last)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val count = bytes / Short.SIZE_BYTES
        if (samples.size < count) samples = ShortArray(count)
        for (i in 0 until count) samples[i] = inputBuffer.getShort()
        val current = mixerFor(inputAudioFormat.sampleRate)
        if (fadeInNext.getAndSet(false)) current.fadeIn()
        current.mix(samples, count, inputAudioFormat.channelCount, options())
        val output = replaceOutputBuffer(count * Short.SIZE_BYTES)
        for (i in 0 until count) output.putShort(samples[i])
        output.flip()
    }

    /** The book's audio ended: the sound fades out after it instead of stopping dead. Not at a sentence's end. */
    override fun onQueueEndOfStream() {
        val tail = mixer?.takeIf { lastItem.get() }?.tail(inputAudioFormat.channelCount)
        if (tail == null || tail.isEmpty()) return
        val output = replaceOutputBuffer(tail.size * Short.SIZE_BYTES)
        tail.forEach(output::putShort)
        output.flip()
    }

    override fun onReset() {
        mixer = null
        samples = ShortArray(0)
        fadeInNext.set(false)
    }

    /** One mixer per sample rate; a voice at another rate carries the sound on where it was. */
    private fun mixerFor(sampleRate: Int): FocusMixer {
        val current = mixer
        return when {
            current == null -> FocusMixer(sampleRate, config, beds)
            current.sampleRate == sampleRate -> current
            else -> current.continuedAt(sampleRate)
        }.also { mixer = it }
    }
}
