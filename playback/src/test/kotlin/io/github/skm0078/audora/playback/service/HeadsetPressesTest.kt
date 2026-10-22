// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/service/HeadsetPressesTest.kt
// Purpose: Headset press bursts (item 20): one plays or pauses, two bookmark, three or more go back a sentence.

package io.github.skm0078.audora.playback.service

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadsetPressesTest {
    private val pending = mutableListOf<() -> Unit>()
    private val gestures = mutableListOf<HeadsetGesture>()
    private val presses = HeadsetPresses(
        windowMs = 300,
        schedule = { _, action ->
            pending += action
            ({ pending.remove(action) })
        },
        onGesture = { gestures += it },
    )

    /** The window passes with no press: the pending end of burst runs. */
    private fun waitOut() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it() }
    }

    @Test
    fun `press counts become gestures once the window passes`() {
        val cases = mapOf(
            1 to HeadsetGesture.PLAY_PAUSE,
            2 to HeadsetGesture.BOOKMARK,
            3 to HeadsetGesture.BACK_ONE_SENTENCE,
            5 to HeadsetGesture.BACK_ONE_SENTENCE,
        )
        cases.forEach { (count, gesture) ->
            gestures.clear()
            repeat(count) { presses.onPress() }
            assertEquals("nothing before the window passes", emptyList<HeadsetGesture>(), gestures)
            waitOut()
            assertEquals("$count presses", listOf(gesture), gestures)
            assertEquals(0, pending.size)
        }
    }

    @Test
    fun `two bursts apart are two gestures`() {
        presses.onPress()
        waitOut()
        presses.onPress()
        presses.onPress()
        waitOut()

        assertEquals(listOf(HeadsetGesture.PLAY_PAUSE, HeadsetGesture.BOOKMARK), gestures)
    }

    @Test
    fun `flush ends a burst at once and does nothing with none`() {
        presses.flush()
        presses.onPress()
        presses.flush()

        assertEquals(listOf(HeadsetGesture.PLAY_PAUSE), gestures)
        assertEquals(0, pending.size)
    }
}
