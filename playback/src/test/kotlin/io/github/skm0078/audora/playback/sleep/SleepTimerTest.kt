// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/sleep/SleepTimerTest.kt
// Purpose: Sleep timer rules: finish the sentence, end of page, paused deadlines, turning it off.

package io.github.skm0078.audora.playback.sleep

import io.github.skm0078.audora.playback.contract.SleepTimerSetting
import io.github.skm0078.audora.playback.contract.SleepTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private val host = FakeHost()
    private val timer = SleepTimer(host)

    @Test
    fun `minutes count down and then finish the sentence`() {
        host.now = 1_000
        timer.set(SleepTimerSetting.AfterMinutes(15))
        assertEquals(SleepTimerState.Counting(15, 1_000 + 15 * MINUTE), timer.state)
        assertFalse(host.armed)

        host.advance(15 * MINUTE)
        assertEquals(SleepTimerState.FinishingSentence, timer.state)
        assertTrue("reading must stop at the sentence end, not now", host.armed)

        timer.onPausedAtEndOfItem()
        assertEquals(SleepTimerState.Off, timer.state)
        assertFalse(host.armed)
    }

    @Test
    fun `time up while paused just turns the timer off`() {
        host.playing = false
        timer.set(SleepTimerSetting.AfterMinutes(1))
        host.advance(MINUTE)
        assertEquals(SleepTimerState.Off, timer.state)
        assertFalse(host.armed)
    }

    @Test
    fun `a deadline that passed while the phone slept is honored on resume`() {
        timer.set(SleepTimerSetting.AfterMinutes(30))
        host.playing = false
        timer.onPausedByUser()
        host.now += 31 * MINUTE // elapsed time moved, the handler delay did not fire
        host.playing = true
        timer.onPlaybackResumed()
        assertEquals(SleepTimerState.Off, timer.state)
        assertTrue(host.tasks.isEmpty())
    }

    @Test
    fun `resume reschedules from the real remaining time`() {
        timer.set(SleepTimerSetting.AfterMinutes(30))
        host.now += 10 * MINUTE
        timer.onPlaybackResumed()
        assertEquals(listOf(20 * MINUTE), host.tasks.map { it.dueAt - host.now })
    }

    @Test
    fun `pausing while finishing the sentence ends the timer`() {
        timer.set(SleepTimerSetting.AfterMinutes(15))
        host.advance(15 * MINUTE)
        timer.onPausedByUser()
        assertEquals(SleepTimerState.Off, timer.state)
        assertFalse(host.armed)
    }

    @Test
    fun `pausing while counting keeps counting`() {
        timer.set(SleepTimerSetting.AfterMinutes(15))
        timer.onPausedByUser()
        assertTrue(timer.state is SleepTimerState.Counting)
    }

    @Test
    fun `end of page arms only on the page's last sentence`() {
        host.lastOfPage = false
        timer.set(SleepTimerSetting.EndOfPage)
        assertEquals(SleepTimerState.EndOfPage, timer.state)
        assertFalse(host.armed)

        host.lastOfPage = true
        timer.onItemOrPlaylistChanged()
        assertTrue(host.armed)

        // The next page was appended after all: the sentence is no longer the last one.
        host.lastOfPage = false
        timer.onItemOrPlaylistChanged()
        assertFalse(host.armed)

        host.lastOfPage = true
        timer.onItemOrPlaylistChanged()
        timer.onPausedAtEndOfItem()
        assertEquals(SleepTimerState.Off, timer.state)
        assertFalse(host.armed)
    }

    @Test
    fun `end of page survives a manual pause`() {
        timer.set(SleepTimerSetting.EndOfPage)
        timer.onPausedByUser()
        assertEquals(SleepTimerState.EndOfPage, timer.state)
    }

    @Test
    fun `turning it off cancels the deadline and disarms`() {
        host.lastOfPage = true
        timer.set(SleepTimerSetting.EndOfPage)
        assertTrue(host.armed)
        timer.set(SleepTimerSetting.Off)
        assertFalse(host.armed)
        assertEquals(SleepTimerState.Off, timer.state)

        timer.set(SleepTimerSetting.AfterMinutes(5))
        timer.set(SleepTimerSetting.Off)
        assertTrue(host.tasks.isEmpty())
    }

    @Test
    fun `a new setting replaces the old one`() {
        timer.set(SleepTimerSetting.AfterMinutes(60))
        timer.set(SleepTimerSetting.AfterMinutes(15))
        assertEquals(1, host.tasks.size)
        assertEquals(SleepTimerState.Counting(15, 15 * MINUTE), timer.state)
    }

    @Test
    fun `every change is published once`() {
        timer.set(SleepTimerSetting.AfterMinutes(15))
        host.advance(15 * MINUTE)
        timer.onPausedAtEndOfItem()
        assertEquals(
            listOf(SleepTimerState.Counting(15, 15 * MINUTE), SleepTimerState.FinishingSentence, SleepTimerState.Off),
            host.published,
        )
    }

    @Test
    fun `an end-of-item pause without a timer changes nothing`() {
        timer.onPausedAtEndOfItem()
        assertEquals(SleepTimerState.Off, timer.state)
        assertTrue(host.published.isEmpty())
    }

    private class Task(val dueAt: Long, val action: () -> Unit)

    private class FakeHost : SleepTimerHost {
        var now = 0L
        var playing = true
        var lastOfPage = false
        var armed = false
        val tasks = mutableListOf<Task>()
        val published = mutableListOf<SleepTimerState>()

        fun advance(ms: Long) {
            now += ms
            val due = tasks.filter { it.dueAt <= now }
            tasks.removeAll(due)
            due.forEach { it.action() }
        }

        override fun nowElapsedMs(): Long = now

        override fun isPlaying(): Boolean = playing

        override fun isLastUnitOfPage(): Boolean = lastOfPage

        override fun setPauseAtEndOfItems(enabled: Boolean) {
            armed = enabled
        }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val task = Task(now + delayMs, action)
            tasks += task
            return Cancellable { tasks.remove(task) }
        }

        override fun publish(state: SleepTimerState) {
            published += state
        }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
