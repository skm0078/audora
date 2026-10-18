// File: app/src/test/kotlin/io/github/skm0078/audora/ui/tutor/TutorModelTest.kt
// Purpose: "Read aloud to me" pauses reading, follows the reader, reports, says words and starts over (item 67).

package io.github.skm0078.audora.ui.tutor

import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.tutor.WordMark
import io.github.skm0078.audora.playback.tutor.Microphone
import io.github.skm0078.audora.playback.tutor.MicrophoneGate
import io.github.skm0078.audora.playback.tutor.TutorError
import io.github.skm0078.audora.playback.tutor.TutorSession
import io.github.skm0078.audora.speech.listen.Heard
import io.github.skm0078.audora.speech.listen.Listener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorModelTest {
    private val config = TutorConfig()
    private val page = TutorPage(page = 4, sentences = listOf("The cat sat.", "It is in."), lang = Lang.EN)

    /** Hears [stretches], one per microphone chunk, each final. */
    private class Hearing(private val stretches: List<String>) : Listener {
        private var at = 0

        override fun accept(samples: FloatArray): List<Heard> =
            stretches.getOrNull(at++)?.let { listOf(Heard(it, final = true)) }.orEmpty()

        override fun finish(): List<Heard> = emptyList()

        override fun close() = Unit
    }

    /** A few chunks, then the microphone ends (as a stop would). */
    private val shortMicrophone = Microphone { onChunk ->
        repeat(CHUNKS) { onChunk(FloatArray(1)) }
        Outcome.Success(Unit)
    }

    private fun TestScope.model(
        listener: Listener?,
        paused: MutableList<Unit> = mutableListOf(),
        spoken: MutableList<String> = mutableListOf(),
    ): TutorModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TutorModel(
            page = page,
            scope = CoroutineScope(dispatcher),
            newSession = { TutorSession(shortMicrophone, MicrophoneGate.NONE, config, dispatcher) },
            openListener = { listener },
            pause = { paused += Unit },
            speak = { text, _ ->
                spoken += text
                true
            },
            config = config,
        )
    }

    @Test
    fun `the page keeps its spoken sentences and the language most are in`() {
        fun unit(index: Int, text: String, lang: Lang) =
            ReadingUnit(0, index, index, index + text.length, text, lang, 0, null)

        val made = TutorPage.of(2, listOf(unit(0, "नमस्ते", Lang.HI), unit(1, "", Lang.EN), unit(2, "आप", Lang.HI)))

        assertEquals(TutorPage(2, listOf("नमस्ते", "आप"), Lang.HI), made)
        assertNull(TutorPage.of(0, listOf(unit(0, " ", Lang.EN))))
    }

    @Test
    fun `listening pauses reading first, follows the reader and reports at the end`() = runTest {
        val paused = mutableListOf<Unit>()
        val model = model(Hearing(listOf("the cat sat", "it is on")), paused)

        model.start()
        assertTrue(model.state.value.listening)
        assertEquals(1, paused.size)
        testScheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.listening)
        assertEquals(List(5) { WordMark.READ } + WordMark.UNREAD, state.along.marks)
        assertEquals(5, state.report?.read)
        assertNull(state.failure)
    }

    @Test
    fun `a missing model is reported and words to check can be heard`() = runTest {
        val spoken = mutableListOf<String>()
        val broken = model(listener = null)
        broken.start()
        testScheduler.advanceUntilIdle()
        assertEquals(TutorError.MODEL_FAILED, broken.state.value.failure)

        val model = model(Hearing(listOf("the cot sat")), spoken = spoken)
        model.start()
        testScheduler.advanceUntilIdle()
        val toCheck = model.state.value.report!!.toCheck
        assertEquals(listOf(1), toCheck)
        model.hear(toCheck.single())
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("cat"), spoken)
    }

    @Test
    fun `start over clears every mark`() = runTest {
        val model = model(Hearing(listOf("the cat sat")))
        model.start()
        testScheduler.advanceUntilIdle()
        assertTrue(model.state.value.started)

        model.startOver()

        assertFalse(model.state.value.started)
        assertNull(model.state.value.report)
    }

    private companion object {
        const val CHUNKS = 3
    }
}
