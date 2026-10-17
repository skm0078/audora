// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/KokoroBlendsTest.kt
// Purpose: A narrator blend mixes only the narrator's style row, keeps the file's shape, and refuses foreign files.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KokoroBlendsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val floatsPerSpeaker = KokoroBlends.SPEAKER_BYTES / Float.SIZE_BYTES

    /** Speaker n's every value is n + 1, so a mix is easy to read back. */
    private fun voicesFile(speakers: Int): File {
        val buffer = ByteBuffer.allocate(speakers * KokoroBlends.SPEAKER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        repeat(speakers) { speaker -> repeat(floatsPerSpeaker) { buffer.putFloat(speaker + 1f) } }
        return folder.newFile("voices.bin").apply { writeBytes(buffer.array()) }
    }

    private fun valueAt(file: File, speaker: Int, index: Int): Float =
        ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            .getFloat(speaker * KokoroBlends.SPEAKER_BYTES + index * Float.SIZE_BYTES)

    @Test
    fun `the narrator's row becomes the weighted mean, every other row is untouched`() {
        val source = voicesFile(speakers = 3)
        val target = File(folder.root, "blends/narrator.bin")

        val made = KokoroBlends.write(source, target, own = 0, blend = StyleBlend(withSpeaker = 2, amount = 0.25f))

        val file = (made as Outcome.Success).value
        assertEquals(source.length(), file.length())
        // 0.75 × 1 + 0.25 × 3
        assertEquals(1.5f, valueAt(file, 0, 0), DELTA)
        assertEquals(1.5f, valueAt(file, 0, floatsPerSpeaker - 1), DELTA)
        assertEquals(2f, valueAt(file, 1, 0), DELTA)
        assertEquals(3f, valueAt(file, 2, 0), DELTA)
        assertEquals(1f, valueAt(source, 0, 0), DELTA)
    }

    @Test
    fun `a file that is not Kokoro styles, or a speaker past the model, is refused`() {
        val odd = folder.newFile("odd.bin").apply { writeBytes(ByteArray(10)) }
        val target = File(folder.root, "out.bin")

        assertTrue(KokoroBlends.write(odd, target, 0, StyleBlend(1, 0.5f)) is Outcome.Failure)
        assertTrue(KokoroBlends.write(voicesFile(2), target, 0, StyleBlend(5, 0.5f)) is Outcome.Failure)
        assertTrue(!target.exists())
    }

    private companion object {
        const val DELTA = 1e-6f
    }
}
