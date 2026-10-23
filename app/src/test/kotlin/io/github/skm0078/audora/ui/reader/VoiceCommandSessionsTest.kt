// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommandSessionsTest.kt
// Purpose: When voice commands listen: screen-off sessions, the idle clock, the on-screen fallback, saved modes.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.commands.ListeningNotice
import io.github.skm0078.audora.speech.kws.ListenFailure
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCommandSessionsTest {
    private class FakeService : ListeningService {
        override val endings = MutableStateFlow(0)
        var starts = 0
        val ends = mutableListOf<ListeningNotice>()
        val silenced = mutableListOf<Boolean>()

        override fun start() {
            starts++
        }

        override fun end(notice: ListeningNotice) {
            ends += notice
        }

        override fun silenced(silenced: Boolean) {
            this.silenced += silenced
        }
    }

    private val service = FakeService()
    private val visible = MutableStateFlow(true)
    private val reading = MutableStateFlow(true)
    private val background = mutableListOf<Boolean>()
    private var listens = 0
    private val forever: Listen = {
        listens++
        awaitCancellation()
    }

    private fun screenOff(listen: Listen = forever) =
        ScreenOffListening(service, listen, visible, reading, IDLE_MS) { background += it }

    @Test
    fun `Stop ends the session, the service is ended without a notice, and the player may let go`() = runTest {
        val session = launch { screenOff().session {} }
        runCurrent()
        assertEquals(1, service.starts)
        assertEquals(listOf(true), background)

        service.endings.value++
        runCurrent()
        assertTrue(session.isCompleted)
        assertEquals(listOf(ListeningNotice.NONE), service.ends)
        assertEquals(listOf(true, false), background)
    }

    @Test
    fun `the idle clock starts at the pause and ends the session once the reader is hidden`() = runTest {
        val session = launch { screenOff().session {} }
        runCurrent()
        reading.value = false
        advanceTimeBy(IDLE_MS + 1)
        assertFalse("still on screen", session.isCompleted)

        visible.value = false
        runCurrent()
        assertTrue(session.isCompleted)
        assertEquals(listOf(ListeningNotice.IDLE), service.ends)
    }

    @Test
    fun `reading again restarts the idle clock`() = runTest {
        visible.value = false
        reading.value = false
        val session = launch { screenOff().session {} }
        advanceTimeBy(IDLE_MS - 1)
        reading.value = true
        runCurrent()
        reading.value = false
        advanceTimeBy(IDLE_MS - 1)
        assertFalse(session.isCompleted)
        advanceTimeBy(2)
        assertTrue(session.isCompleted)
    }

    @Test
    fun `a listener that stops with the reader hidden ends the session with a notice`() = runTest {
        val stopped = CompletableDeferred<Outcome<Unit, ListenFailure>>()
        val session = launch { screenOff { stopped.await() }.session {} }
        runCurrent()
        visible.value = false
        stopped.complete(Outcome.Failure(ListenFailure.MICROPHONE_LOST))
        runCurrent()
        assertTrue(session.isCompleted)
        assertEquals(listOf(ListeningNotice.MICROPHONE_LOST), service.ends)
    }

    @Test
    fun `after a session ends on screen it listens on screen, and the next showing starts a new session`() = runTest {
        val listen: Listen = {
            listens++
            if (listens == 1) Outcome.Failure(ListenFailure.MICROPHONE_LOST) else awaitCancellation()
        }
        val running = launch { screenOff(listen).run {} }
        runCurrent()
        assertEquals(listOf(ListeningNotice.NONE), service.ends)
        assertEquals("fell back to listening on screen", 2, listens)

        visible.value = false
        runCurrent()
        visible.value = true
        runCurrent()
        assertEquals(2, service.starts)
        running.cancel()
    }

    @Test
    fun `listening on screen does nothing while hidden and stops when hidden`() = runTest {
        visible.value = false
        listenWhileVisible(visible, forever) {}
        assertEquals(0, listens)

        visible.value = true
        val listening = launch { listenWhileVisible(visible, forever) {} }
        runCurrent()
        assertEquals(1, listens)
        visible.value = false
        runCurrent()
        assertTrue(listening.isCompleted)
    }

    @Test
    fun `commands arrive in the order heard and silencing reaches the service`() = runTest {
        val heard = mutableListOf<VoiceCommand>()
        val outcome = hear(
            listen = { events ->
                events.onCommand(VoiceCommand.PAUSE)
                events.onSilenced(true)
                events.onCommand(VoiceCommand.NEXT)
                Outcome.Success(Unit)
            },
            onSilenced = service::silenced,
        ) { heard += it }
        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(listOf(VoiceCommand.PAUSE, VoiceCommand.NEXT), heard)
        assertEquals(listOf(true), service.silenced)
    }

    @Test
    fun `the player stays connected while hidden only during a session`() = runTest {
        val inBackground = MutableStateFlow(false)
        val wanted = mutableListOf<Boolean>()
        val collecting = launch { playerWanted(visible, inBackground).toList(wanted) }
        runCurrent()
        inBackground.value = true
        runCurrent()
        visible.value = false
        runCurrent()
        inBackground.value = false
        runCurrent()
        assertEquals(listOf(true, false), wanted)
        collecting.cancel()
    }

    @Test
    fun `a saved mode maps back by name and runs only with what it needs`() {
        assertEquals(VoiceCommandsMode.SCREEN_OFF, VoiceCommandsMode.fromSaved("SCREEN_OFF"))
        assertNull(VoiceCommandsMode.fromSaved("ALWAYS"))
        assertNull(VoiceCommandsMode.fromSaved(null))

        val screenOff = VoiceCommandsMode.SCREEN_OFF
        assertEquals(VoiceCommandsMode.OFF, screenOff.effective(microphone = false, notifications = true))
        assertEquals(VoiceCommandsMode.WHILE_VISIBLE, screenOff.effective(microphone = true, notifications = false))
        assertEquals(screenOff, screenOff.effective(microphone = true, notifications = true))
        assertEquals(
            VoiceCommandsMode.WHILE_VISIBLE,
            VoiceCommandsMode.WHILE_VISIBLE.effective(microphone = true, notifications = false),
        )
    }

    private companion object {
        const val IDLE_MS = 600_000L
    }
}
