// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/PitchedChapterWriter.kt
// Purpose: A saved file at the listener's pitch (ADR-067): the voice's PCM through Media3's Sonic before the encoder.

package io.github.skm0078.audora.playback.export

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * [writer] at [pitch] when it is not the voice's own, else [writer] itself. Playback shifts the pitch in the player;
 * an audiobook, a clip, a studio file or a talking book has no player of ours, so the same Sonic shifts it before the
 * encoder and the file sounds like listening.
 */
fun ChapterAudioWriter.atPitch(pitch: Float): ChapterAudioWriter =
    if (abs(pitch - SpeechStyle.NORMAL_PITCH) < PITCH_EPSILON) this else PitchedChapterWriter(this, pitch)

/**
 * [signature] for a resumable job's key, with the pitch when it is not the voice's own: an export or a talking book
 * left half done and resumed at another pitch starts again instead of mixing two pitches in one book. A job at the
 * voice's own pitch keeps the key it had before pitch existed.
 */
fun withPitch(signature: String, pitch: Float): String =
    if (abs(pitch - SpeechStyle.NORMAL_PITCH) < PITCH_EPSILON) signature else "$signature|pitch=$pitch"

/**
 * One chapter's stream through one Sonic, so sentence joins are shifted like the rest (no click at each one). The
 * shifted audio comes out a few milliseconds behind what went in; [finish] drains it, so nothing is lost and the
 * file is as long as the voice's audio. Thread-confined like the writer it wraps.
 */
@OptIn(UnstableApi::class)
internal class PitchedChapterWriter(private val writer: ChapterAudioWriter, pitch: Float) : ChapterAudioWriter {
    private val sonic = SonicAudioProcessor().apply {
        setPitch(pitch)
        configure(AudioProcessor.AudioFormat(writer.sampleRate, CHANNELS, C.ENCODING_PCM_16BIT))
        flush(AudioProcessor.StreamMetadata.DEFAULT)
    }
    private var input: ByteBuffer = ByteBuffer.allocateDirect(0)

    override val sampleRate: Int get() = writer.sampleRate

    override fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage> {
        require(audio.sampleRate == sampleRate) { "Audio at ${audio.sampleRate} Hz in a $sampleRate Hz file" }
        val bytes = audio.samples.size * Short.SIZE_BYTES
        if (input.capacity() < bytes) input = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        input.clear()
        input.asShortBuffer().put(audio.samples)
        input.limit(bytes)
        sonic.queueInput(input) // Sonic takes all of it into its own buffer.
        return writeShifted()
    }

    override fun finish(): Outcome<ExportedChapter, ExportError.Storage> {
        sonic.queueEndOfStream()
        val tail = writeShifted()
        sonic.reset()
        return when (tail) {
            is Outcome.Success -> writer.finish()
            is Outcome.Failure -> tail
        }
    }

    override fun abort() {
        sonic.reset()
        writer.abort()
    }

    /** What Sonic has ready, into the file. */
    private fun writeShifted(): Outcome<Unit, ExportError.Storage> {
        val output = sonic.output
        if (!output.hasRemaining()) return Outcome.Success(Unit)
        val samples = ShortArray(output.remaining() / Short.SIZE_BYTES)
        output.asShortBuffer().get(samples)
        return writer.write(PcmAudio(samples, sampleRate))
    }

    private companion object {
        const val CHANNELS = 1
    }
}

/** Pitches are snapped to 0.05 steps, so anything closer to 1 than this is the voice's own. */
private const val PITCH_EPSILON = 0.001f
