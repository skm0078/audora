// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/ListenLoopTest.kt
// Purpose: The listening loop lets the microphone go for a hold and back, closes it on every exit, and gates quiet.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenLoopTest {
    private val config = VoiceCommandConfig(chunkMs = 10, preRollChunks = 1, hangoverMs = 10, silenceCheckMs = 1)
    private val pause = VoiceCommandPause(releaseWaitMs = 1_000)
    private val echo = NarratorEcho(holdMs = 0)
    private var now = 0L

    /** Plays [script] chunk by chunk: a Short is a chunk at that level, null a read error; [after] runs per read. */
    private inner class FakeMicrophone(
        private val script: List<Short?>,
        private val after: (Int) -> Unit = {},
        private val silencedAt: Set<Int> = emptySet(),
    ) : CommandMicrophone {
        var reads = 0
        var closed = 0

        override fun read(pcm: ShortArray): Int {
            val level = script.getOrNull(reads)
            val count = if (reads < script.size && level != null) pcm.size else ERROR
            pcm.fill(level ?: 0)
            reads++
            now += config.chunkMs
            after(reads)
            return count
        }

        override fun silenced(): Boolean = reads in silencedAt

        override fun close() {
            closed++
        }
    }

    /** Hears [command] in every chunk louder than zero. */
    private val decoder = KeywordDecoder { samples -> VoiceCommand.NEXT.takeIf { samples.any { it != 0f } } }
    private val decoded = mutableListOf<FloatArray>()

    private fun loop(vararg microphones: CommandMicrophone?): ListenLoop {
        val queue = ArrayDeque(microphones.toList())
        val recording = KeywordDecoder {
            decoded.add(it)
            decoder.accept(it)
        }
        return ListenLoop(recording, { queue.removeFirstOrNull() }, config, echo, pause, { now })
    }

    @Test
    fun `a read error ends listening as microphone lost, closed and released`() = runTest {
        val microphone = FakeMicrophone(listOf(LOUD, null))
        val heard = mutableListOf<VoiceCommand>()

        val outcome = loop(microphone).run(ListenEvents { heard.add(it) })

        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_LOST), outcome)
        assertEquals(listOf(VoiceCommand.NEXT), heard)
        assertEquals(1, microphone.closed)
        assertTrue(pause.claimMicrophone()) // nothing left claimed
    }

    @Test
    fun `a hold lets the microphone go and listening opens it again after`() = runTest {
        lateinit var hold: Deferred<VoiceCommandPause.Hold>
        val first = FakeMicrophone(List(10) { QUIET }, after = { if (it == 2) hold = async { pause.hold("tutor") } })
        val second = FakeMicrophone(listOf(null))
        val run = async { loop(first, second).run(ListenEvents {}) }
        runCurrent()

        assertEquals(1, first.closed)
        assertTrue(hold.isCompleted)
        assertFalse(run.isCompleted)
        hold.await().close()
        runCurrent()
        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_LOST), run.await())
        assertEquals(1, second.closed)
    }

    @Test
    fun `cancelling closes the microphone and ends its claim`() = runTest {
        lateinit var job: Job
        val microphone = FakeMicrophone(List(10) { QUIET }, after = { if (it == 3) job.cancel() })
        job = launch { loop(microphone).run(ListenEvents {}) }
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(1, microphone.closed)
        assertTrue(pause.claimMicrophone())
    }

    @Test
    fun `a microphone that will not open is busy, unless a hold came in, then it is tried again after`() = runTest {
        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_BUSY), loop(null).run(ListenEvents {}))

        var opens = 0
        var hold: VoiceCommandPause.Hold? = null
        val retried = FakeMicrophone(listOf(null))
        val holdWhileOpening = {
            opens++
            if (opens == 1) {
                // A feature takes the microphone while the listener is opening it: its hold registers at once.
                launch(start = CoroutineStart.UNDISPATCHED) { hold = pause.hold("tutor") }
                null
            } else {
                retried
            }
        }
        val run = async { ListenLoop(decoder, holdWhileOpening, config, echo, pause, { now }).run(ListenEvents {}) }
        runCurrent()
        assertFalse(run.isCompleted)
        hold!!.close()
        runCurrent()
        assertEquals(Outcome.Failure(ListenFailure.MICROPHONE_LOST), run.await())
        assertEquals(2, opens)
    }

    @Test
    fun `a close that throws still ends the claim`() = runTest {
        val throwing = object : CommandMicrophone {
            override fun read(pcm: ShortArray): Int = ERROR

            override fun silenced(): Boolean = false

            override fun close() = error("recorder already released")
        }
        runCatching { loop(throwing).run(ListenEvents {}) }
        assertTrue(pause.claimMicrophone())
    }

    @Test
    fun `quiet chunks are not decoded, the one before a loud chunk is, and the tail after it`() = runTest {
        val microphone = FakeMicrophone(listOf(QUIET, QUIET, LOUD, QUIET, QUIET, QUIET, null))

        loop(microphone).run(ListenEvents {})

        // The second quiet chunk as pre-roll, the loud one, then one chunk of hangover (10 ms).
        assertEquals(listOf(QUIET, LOUD, QUIET), decoded.map { (it[0] * FULL_SCALE).toInt().toShort() })
    }

    @Test
    fun `while Android silences the capture nothing is decoded, and each change is reported`() = runTest {
        val microphone = FakeMicrophone(listOf(LOUD, LOUD, LOUD, null), silencedAt = setOf(1, 2))
        val reports = mutableListOf<Boolean>()

        loop(microphone).run(ListenEvents(onSilenced = { reports.add(it) }) {})

        assertEquals(listOf(true, false), reports)
        assertEquals(1, decoded.size)
    }

    @Test
    fun `the narrator saying the app's name and a repeat within the gap act on nothing`() = runTest {
        val narrator = NarratorEcho(holdMs = 0)
        narrator.sentenceStarted("Audora said", 0)
        val heard = mutableListOf<VoiceCommand>()
        val microphone = FakeMicrophone(listOf(LOUD, LOUD, LOUD, null), after = { if (it == 2) narrator.stopped(now) })

        ListenLoop(decoder, { microphone }, config, narrator, pause, { now }).run(ListenEvents { heard.add(it) })

        // The first was the narrator; the second acted; the third repeats it within the gap.
        assertEquals(listOf(VoiceCommand.NEXT), heard)
    }

    private companion object {
        const val ERROR = -3
        const val QUIET: Short = 10
        const val LOUD: Short = 8_000
        const val FULL_SCALE = 32_768f
    }
}
