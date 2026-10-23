// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/StudioPageTest.kt
// Purpose: A studio page survives the trip to disk, matches only unchanged sentences, and detects damage.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.unit
import java.io.DataInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudioPageTest {
    private val codec = LosslessPcm(BLOCK)

    private fun page(): StudioPage = StudioPage(
        page = 3,
        clips = listOf(
            StudioClip.of(unit(3, 0), StudioVoiced(pcm(1_500, fill = 5)), codec),
            StudioClip.of(unit(3, 1), StudioVoiced(pcm(500, fill = -9)), codec),
        ),
    )

    @Test
    fun `keeps where the voice is in each clip`() {
        val clip = StudioClip.of(unit(3, 0), StudioVoiced(pcm(1_500), leadMs = 120, speechMs = 900), codec)
        val read = StudioPage.fromBytes(StudioPage(3, listOf(clip)).toBytes()).clips.single()
        assertEquals(120L, read.leadMs)
        assertEquals(900L, read.speechMs)
    }

    @Test
    fun `reads back what it wrote`() {
        val read = StudioPage.fromBytes(page().toBytes())
        assertEquals(3, read.page)
        assertEquals(2, read.clips.size)
        assertEquals(2_000L, read.durationMs)
        val clip = read.clipFor(unit(3, 0))
        assertNotNull(clip)
        val audio = clip!!.audio(codec)
        assertArrayEquals(pcm(1_500, fill = 5).samples, audio.samples)
    }

    @Test
    fun `the length is read from the headers alone`() {
        val input = DataInputStream(page().toBytes().inputStream())
        assertEquals(2_000L, StudioPage.durationOf(input))
    }

    @Test
    fun `a cut-short file reads as damaged, not as shorter`() {
        val bytes = page().toBytes()
        try {
            StudioPage.durationOf(DataInputStream(bytes.copyOf(bytes.size / 2).inputStream()))
            fail("A truncated page must not have a length")
        } catch (expected: CorruptStudioData) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun `a sentence whose text changed does not match`() {
        val changed = unit(3, 1).copy(text = "Another text.")
        assertNull(page().clipFor(changed))
        assertFalse(page().covers(listOf(unit(3, 0), changed)))
        assertTrue(page().covers(listOf(unit(3, 0), unit(3, 1))))
    }

    @Test
    fun `moving a sentence on the page keeps its audio`() {
        val moved = unit(3, 0).copy(charStart = 100, charEnd = 109)
        assertNotNull(page().clipFor(moved))
    }

    @Test
    fun `a flipped byte is caught by the checksum`() {
        val bytes = page().toBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        try {
            StudioPage.fromBytes(bytes)
            fail("read a damaged page")
        } catch (expected: CorruptStudioData) {
            // Expected.
        }
    }

    private companion object {
        const val BLOCK = 256
    }
}
