// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/tutor/TutorSessionTest.kt
// Purpose: A listening run follows the reader to the page's end, stops cleanly, and always frees the speech model.

package io.github.skm0078.audora.playback.tutor

import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.tutor.PageWords
import io.github.skm0078.audora.core.tutor.ReadAlong
import io.github.skm0078.audora.core.tutor.WordMark
import io.github.skm0078.audora.speech.listen.Heard
import io.github.skm0078.audora.speech.listen.Listener
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSessionTest {
    private val config = TutorConfig()
    private val start = ReadAlong.start(PageWords.of(listOf("The cat sat."), Lang.EN), config)

    /** Hears the scripted stretches, one per chunk; then the stretch [onFinish] when the microphone stops. */
    private class ScriptedListener(
        private val script: List<List<Heard>>,
        private val onFinish: List<Heard> = emptyList(),
    ) : Listener {
        var chunks = 0
        var finished = false
        var closed = false

        override fun accept(samples: FloatArray): List<Heard> = script.getOrElse(chunks++) { emptyList() }

        override fun finish(): List<Heard> = onFinish.also { finished = true }

        override fun close() {
            closed = true
        }
    }

    /** Sends a chunk forever (until cancelled), counting them. */
    private class EndlessMicrophone : Microphone {
        override suspend fun record(onChunk: suspend (FloatArray) -> Unit): Outcome<Unit, TutorError> {
            while (true) onChunk(FloatArray(1))
        }
    }

    private class RecordingGate : MicrophoneGate {
        val reasons = mutableListOf<String>()
        var held = 0

        override suspend fun hold(reason: String): AutoCloseable {
            reasons += reason
            held++
            return AutoCloseable { held-- }
        }
    }

    @Test
    fun `the run ends when the page is read, holding the microphone meanwhile`() = runTest {
        val listener = ScriptedListener(
            listOf(listOf(Heard("the cat", final = false)), listOf(Heard("the cat sat", final = true))),
        )
        val gate = RecordingGate()
        val changes = mutableListOf<ReadAlong>()
        val session = TutorSession(EndlessMicrophone(), gate, config, StandardTestDispatcher(testScheduler))

        val outcome = session.listen(start, { listener }) { changes += it }

        val along = (outcome as Outcome.Success).value
        assertTrue(along.finished)
        assertEquals(List(3) { WordMark.READ }, along.marks)
        assertEquals(2, changes.size)
        assertEquals(listOf("reading tutor"), gate.reasons)
        assertEquals(0, gate.held)
        assertTrue(listener.closed)
    }

    @Test
    fun `stopping finishes the stretch under way and counts it`() = runTest {
        val listener = ScriptedListener(
            listOf(listOf(Heard("the", final = false))),
            onFinish = listOf(Heard("the cat", final = true)),
        )
        val session =
            TutorSession(EndlessMicrophone(), MicrophoneGate.NONE, config, StandardTestDispatcher(testScheduler))
        val heardOnce = CompletableDeferred<Unit>()

        val run = async { session.listen(start, { listener }) { heardOnce.complete(Unit) } }
        heardOnce.await()
        session.stop()
        val along = (run.await() as Outcome.Success).value

        assertTrue(listener.finished)
        assertEquals(listOf(WordMark.READ, WordMark.READ, WordMark.UNREAD), along.marks)
        assertEquals(2, along.next)
        assertTrue(listener.closed)
    }

    @Test
    fun `a model that will not load and a microphone that will not open are reported`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val noModel = TutorSession(EndlessMicrophone(), MicrophoneGate.NONE, config, dispatcher)
        assertEquals(Outcome.Failure(TutorError.MODEL_FAILED), noModel.listen(start, { null }) {})

        val listener = ScriptedListener(emptyList())
        val refused = Microphone { Outcome.Failure(TutorError.NO_PERMISSION) }
        val noMic = TutorSession(refused, MicrophoneGate.NONE, config, dispatcher)
        assertEquals(Outcome.Failure(TutorError.NO_PERMISSION), noMic.listen(start, { listener }) {})
        assertTrue(listener.closed)
    }

    @Test
    fun `cancelling the caller still frees the model`() = runTest {
        val listener = ScriptedListener(emptyList())
        val silent = Microphone { awaitCancellation() }
        val session = TutorSession(silent, MicrophoneGate.NONE, config, StandardTestDispatcher(testScheduler))

        val run = async { session.listen(start, { listener }) {} }
        testScheduler.advanceUntilIdle()
        run.cancel()
        testScheduler.advanceUntilIdle()

        assertTrue(listener.closed)
    }

    @Test
    fun `cancelling while the model loads still frees it, and the microphone is never held`() = runTest {
        val listener = ScriptedListener(emptyList())
        val loading = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val gate = RecordingGate()
        val session = TutorSession(EndlessMicrophone(), gate, config, Dispatchers.IO)

        val run = async(Dispatchers.Default) {
            session.listen(start, {
                loading.complete(Unit)
                release.await()
                listener
            }) {}
        }
        loading.await()
        run.cancel()
        release.countDown()
        run.join()

        assertTrue(run.isCancelled)
        assertTrue(listener.closed)
        assertTrue(gate.reasons.isEmpty())
    }

    @Test
    fun `a native decode error is reported as a damaged model`() = runTest {
        val broken = object : Listener {
            var closed = false

            override fun accept(samples: FloatArray): List<Heard> = throw IllegalArgumentException("native")

            override fun finish(): List<Heard> = emptyList()

            override fun close() {
                closed = true
            }
        }
        val gate = RecordingGate()
        val session = TutorSession(EndlessMicrophone(), gate, config, StandardTestDispatcher(testScheduler))

        assertEquals(Outcome.Failure(TutorError.MODEL_FAILED), session.listen(start, { broken }) {})
        assertTrue(broken.closed)
        assertEquals(0, gate.held)
    }
}
