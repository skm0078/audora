// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/RecognitionLoopTest.kt
// Purpose: The recogniser loop acts on each command once across growing partial results, restarts after a session
//          ends, gives up (and says why) only when it must, lets the microphone go for a hold, and says "Listening"
//          only while it truly listens.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionLoopTest {
    private val config = VoiceCommandConfig(failureRetryMs = 100, maxFailuresInARow = 3, repeatGapMs = 1_500)
    private val pause = VoiceCommandPause(releaseWaitMs = 1_000)
    private val echo = NarratorEcho(holdMs = 0)
    private var now = 10_000L
    private val heard = mutableListOf<VoiceCommand>()
    private val listening = mutableListOf<Boolean>()
    private val events = ListenEvents(onMicrophone = { listening += it }) { heard += it }

    /** One scripted session: the texts it reports, then how it ends; null [end] listens until cancelled. */
    private class Script(val texts: List<String> = emptyList(), val end: SessionEnd? = SessionEnd.HEARD)

    /** Plays [scripts] in turn, then reports permission lost so a test's loop always ends. */
    private inner class FakeRecognizer(vararg scripts: Script) : CommandRecognizer {
        private val queue = ArrayDeque(scripts.toList())
        var sessions = 0
        var cancelled = 0

        override suspend fun session(onReady: () -> Unit, onText: (String) -> Unit): SessionEnd {
            sessions++
            val script = queue.removeFirstOrNull() ?: return SessionEnd.NO_PERMISSION
            onReady()
            script.texts.forEach {
                now += TEXT_GAP_MS
                onText(it)
            }
            return script.end ?: try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
    }

    private fun loop(recognizer: CommandRecognizer) = RecognitionLoop(recognizer, config, echo, pause) { now }

    @Test
    fun `a command acts once however many partial results repeat it, then the next session starts`() = runTest {
        val recognizer = FakeRecognizer(
            Script(listOf("listen", "listen lock", "listen lock pause", "Listen lock, pause.")),
            Script(listOf("ListenLock play")),
        )

        val outcome = loop(recognizer).run(events)

        assertEquals(listOf(VoiceCommand.PAUSE, VoiceCommand.PLAY), heard)
        assertEquals(Outcome.Failure(ListenFailure.NO_PERMISSION), outcome)
        assertEquals(3, recognizer.sessions)
        assertEquals("on once, off once at the end", listOf(true, false), listening)
    }

    @Test
    fun `two commands said in one go both act, in order`() = runTest {
        loop(FakeRecognizer(Script(listOf("ListenLock pause", "ListenLock pause listen lock next")))).run(events)

        assertEquals(listOf(VoiceCommand.PAUSE, VoiceCommand.NEXT), heard)
    }

    @Test
    fun `a quiet session or a lone failure is retried, and listening never ends for it`() = runTest {
        val recognizer = FakeRecognizer(
            Script(end = SessionEnd.NOTHING),
            Script(end = SessionEnd.FAILED),
            Script(end = SessionEnd.FAILED),
            Script(listOf("listen lock next")),
            Script(end = SessionEnd.FAILED),
        )

        loop(recognizer).run(events)

        assertEquals(listOf(VoiceCommand.NEXT), heard)
        assertEquals("a heard session resets the failure count", 6, recognizer.sessions)
    }

    @Test
    fun `failures in a row stop listening and say why`() = runTest {
        val failing = List(3) { Script(end = SessionEnd.FAILED) }.toTypedArray()
        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_LOST), loop(FakeRecognizer(*failing)).run(events))

        val busy = List(3) { Script(end = SessionEnd.BUSY) }.toTypedArray()
        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_BUSY), loop(FakeRecognizer(*busy)).run(events))
    }

    @Test
    fun `a phone that cannot listen says so at once`() = runTest {
        val missing = loop(FakeRecognizer(Script(end = SessionEnd.LANGUAGE_MISSING))).run(events)
        val none = loop(FakeRecognizer(Script(end = SessionEnd.NOT_AVAILABLE))).run(events)

        assertEquals(Outcome.Failure(ListenFailure.LANGUAGE_MISSING), missing)
        assertEquals(Outcome.Failure(ListenFailure.NOT_AVAILABLE), none)
    }

    @Test
    fun `a hold stops the session at once, and listening comes back after it`() = runTest {
        val recognizer = FakeRecognizer(Script(end = null), Script(listOf("listen lock play")))
        val run = async { loop(recognizer).run(events) }
        runCurrent()
        assertEquals(listOf(true), listening)

        val released = CompletableDeferred<Unit>()
        val holding = async { pause.holding("the tutor") { released.await() } }
        runCurrent()
        assertEquals("the session was stopped for the hold", 1, recognizer.cancelled)
        assertEquals(listOf(true, false), listening)
        assertTrue("the hold got the microphone", pause.isPaused)

        released.complete(Unit)
        holding.await()
        run.await()
        assertEquals(listOf(VoiceCommand.PLAY), heard)
        assertEquals(listOf(true, false, true, false), listening)
    }

    @Test
    fun `nothing acts while the book itself says the app's name`() = runTest {
        echo.sentenceStarted("And then ListenLock said: pause.", now)

        loop(FakeRecognizer(Script(listOf("listen lock pause")))).run(events)

        assertEquals(emptyList<VoiceCommand>(), heard)
    }

    private companion object {
        const val TEXT_GAP_MS = 200L
    }
}
