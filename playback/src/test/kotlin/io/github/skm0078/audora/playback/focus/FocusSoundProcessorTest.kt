// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/focus/FocusSoundProcessorTest.kt
// Purpose: The focus sound carries on across sentence changes and fades out only after the book's last sentence.

package io.github.skm0078.audora.playback.focus

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION") // flush(): the overload every Media3 1.x keeps; the test needs no stream metadata.
class FocusSoundProcessorTest {
    private val processor = FocusSoundProcessor({ FocusSoundOptions(FocusSound.RAIN, level = 1f) }, beds = TEST_BEDS)

    private fun start() {
        processor.configure(AudioProcessor.AudioFormat(RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        play(ms = 1_000) // past the sound's own fade-in
    }

    /** Queues [ms] of a silent voice and returns what came out. */
    private fun play(ms: Int): ShortArray {
        val bytes = ByteBuffer.allocateDirect(RATE * ms / 1_000 * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        processor.queueInput(bytes)
        return drain()
    }

    private fun drain(): ShortArray {
        val out = processor.output.asShortBuffer()
        return ShortArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun `a sentence's end adds no tail, the book's last one does`() {
        start()
        processor.endsAfterCurrent(false)
        processor.queueEndOfStream()
        assertEquals(0, drain().size)

        processor.flush()
        play(ms = 100)
        processor.endsAfterCurrent(true)
        processor.queueEndOfStream()
        assertTrue(drain().size >= RATE * TAIL_MS / 1_000 / 2)
    }

    @Test
    fun `the sound carries on across a sentence change and fades in only when told`() {
        start()
        val before = rms(play(ms = 50))

        processor.flush() // what Media3 does between two sentences
        val carried = rms(play(ms = 20))
        processor.fadeIn() // a seek or a stall
        val faded = rms(play(ms = 20))

        assertTrue("carried $carried vs before $before", carried > before / 2)
        assertTrue("faded $faded vs carried $carried", faded < carried / 2)
    }

    private fun rms(samples: ShortArray): Double =
        sqrt(samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1))

    private companion object {
        const val RATE = 22_050
        const val TAIL_MS = 600
    }
}
