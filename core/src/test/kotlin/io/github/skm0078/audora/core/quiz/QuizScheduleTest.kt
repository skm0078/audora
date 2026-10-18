// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizScheduleTest.kt
// Purpose: Right answers space a card out, a wrong one brings it back; progress survives in its file.

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.model.Lang
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizScheduleTest {
    private val config = QuizConfig(sessionCards = 3, intervalsMs = listOf(100L, 1_000L), retryMs = 10L)
    private val schedule = QuizSchedule(config)
    private val folder = Files.createTempDirectory("quiz").toFile()

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `due cards come first, longest overdue first, then new ones in order, up to the session size`() {
        val cards = (1L..5L).map(::card)
        val progress = mapOf(
            2L to CardProgress(1, dueAtMs = 50),
            4L to CardProgress(1, dueAtMs = 20),
            5L to CardProgress(2, dueAtMs = 900),
        )

        val picked = schedule.pick(cards, progress, nowMs = 100)

        assertEquals(listOf(4L, 2L, 1L), picked.map { it.highlightId })
    }

    @Test
    fun `each right answer waits longer, the last interval repeats, a wrong one starts over`() {
        var progress = emptyMap<Long, CardProgress>()
        val waits = listOf(true, true, true, false).mapIndexed { i, right ->
            val now = i * 10_000L
            progress = schedule.answered(progress, 1, right, now)
            progress.getValue(1).dueAtMs - now
        }

        assertEquals(listOf(100L, 1_000L, 1_000L, 10L), waits)
        assertEquals(0, progress.getValue(1).streak)
    }

    @Test
    fun `progress round-trips, drops cards whose highlight is gone and ignores a foreign file`() {
        val store = QuizProgressStore(folder.resolve(QuizProgressStore.FILE_NAME))
        val progress = mapOf(1L to CardProgress(2, 500), 9L to CardProgress(0, 7))

        store.save(progress, known = setOf(1L))

        assertEquals(mapOf(1L to CardProgress(2, 500)), store.load())
        folder.resolve(QuizProgressStore.FILE_NAME).writeText("something else\n1\t2\t3\n")
        assertEquals(emptyMap<Long, CardProgress>(), store.load())
    }

    private fun card(id: Long) = QuizCard(id, "x", "x", "y", emptyList(), Lang.EN)
}
