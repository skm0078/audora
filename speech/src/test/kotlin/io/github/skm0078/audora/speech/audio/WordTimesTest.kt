// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/audio/WordTimesTest.kt
// Purpose: Word starts read from the voice's audio (CF-10): punctuation pinned to pauses, letters share the rest.

package io.github.skm0078.audora.speech.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordTimesTest {
    private val times = WordTimes()

    @Test
    fun `a comma waits through the voice's pause, and the next word starts on the voice`() {
        // 300 ms of lead silence, "Hello," for 600 ms, a 250 ms pause, "world again." for 1200 ms.
        val audio = audioOf(silence(300), tone(600), silence(250), tone(1_200), silence(200))

        val starts = times.of("Hello, world again.", audio)!!

        assertEquals(3, starts.size)
        assertNear(300, starts[0])
        assertNear(1_150, starts[1])
        // "world" and "again" have five letters each: half of the second stretch each.
        assertNear(1_750, starts[2])
    }

    @Test
    fun `without the pause the old letter spread was a quarter second late on the word after the comma`() {
        val audio = audioOf(tone(600), silence(400), tone(600))
        val starts = times.of("Hi, there", audio)!!

        // By letters alone "there" (5 of 7 letters) would start at 2/7 of 1600 ms, about 457 ms: inside "Hi,".
        assertNear(1_000, starts[1])
    }

    @Test
    fun `a breath where no mark is is passed over, and the letters spread over the whole speech`() {
        val audio = audioOf(tone(500), silence(200), tone(500))

        val starts = times.of("aaaa bbbb cccc dddd", audio)!!

        listOf(0L, 300L, 600L, 900L).zip(starts).forEach { (expected, actual) -> assertNear(expected, actual) }
    }

    @Test
    fun `a mark the voice ran straight through is not forced onto a far pause`() {
        // The comma falls at about 10 % of the speech; the only pause is at 85 %, far outside the tolerance.
        val audio = audioOf(tone(1_700), silence(150), tone(300))

        val starts = times.of("A, bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", audio)!!

        assertNear(0, starts[0])
        // One letter of 37 for "A,", so "b…" starts about 2150 / 37 ms in.
        assertNear(58, starts[1])
    }

    @Test
    fun `Hindi and Bengali stops and dandas pin too, vowel signs count as letters`() {
        val audio = audioOf(tone(800), silence(300), tone(800))

        val hindi = times.of("राम आया। सीता गई।", audio)!!
        val bengali = times.of("আমি এলাম, তুমি গেলে।", audio)!!

        assertNear(1_100, hindi[2])
        assertNear(1_100, bengali[2])
    }

    @Test
    fun `no words or no sound gives no times`() {
        assertNull(times.of("   ", audioOf(tone(500))))
        assertNull(times.of("Hello there.", audioOf(silence(800))))
        assertNull(times.of("Hello there.", PcmAudio(ShortArray(0), RATE)))
    }

    @Test
    fun `there is one start per word as whitespace separates them, punctuation riding with its word`() {
        val audio = audioOf(tone(900))
        val text = "  “Yes,” she\tsaid —  quietly. "

        assertEquals(5, times.of(text, audio)!!.size)
    }

    private fun assertNear(expected: Long, actual: Long) =
        assertEquals("expected about $expected ms, was $actual", expected.toDouble(), actual.toDouble(), TOLERANCE_MS)

    private fun audioOf(vararg parts: ShortArray) = PcmAudio(parts.reduce { a, b -> a + b }, RATE)

    private fun silence(ms: Int) = ShortArray(RATE * ms / 1_000)

    private fun tone(ms: Int) = ShortArray(RATE * ms / 1_000) { i ->
        (8_000 * sin(2 * PI * 220 * i / RATE)).toInt().toShort()
    }

    private companion object {
        const val RATE = 22_050
        const val TOLERANCE_MS = 20.0
    }
}
