// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizScheduleTest.kt
// Purpose: Right answers space a card out, a wrong one brings it back; progress survives in its file.

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
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
        val cards = (1L..5L).toList()
        val progress = mapOf(
            2L to CardProgress(1, dueAtMs = 50),
            4L to CardProgress(1, dueAtMs = 20),
            5L to CardProgress(2, dueAtMs = 900),
        )

        val picked = schedule.pick(cards, progress, nowMs = 100)

        assertEquals(listOf(4L, 2L, 1L), picked)
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
        store.save(progress, known = setOf(9L))
        val aside = folder.listFiles()!!.single { it.name.contains(".unreadable-") }
        assertEquals("a foreign file is kept, not overwritten", "something else\n1\t2\t3\n", aside.readText())
    }

    @Test
    fun `practise asks the soonest cards by their real due time and never promotes them`() {
        val progress = mapOf(1L to CardProgress(3, 9_000), 2L to CardProgress(1, 2_000), 3L to CardProgress(2, 5_000))

        assertEquals(listOf(2L, 3L, 1L), schedule.practise(listOf(1L, 2L, 3L, 4L), progress))
        assertEquals(progress, schedule.answered(progress, 2, right = true, nowMs = 100))
        assertEquals(CardProgress(0, 110), schedule.answered(progress, 2, right = false, nowMs = 100).getValue(2))
    }

    @Test
    fun `malformed lines are skipped, an empty file is no progress, a failed save keeps the old file`() {
        val file = folder.resolve(QuizProgressStore.FILE_NAME)
        val store = QuizProgressStore(file)
        file.writeText("audora-quiz 1\n1\t2\t500\nx\ty\tz\n3\t-1\t9\n4\t1\n")
        assertEquals(mapOf(1L to CardProgress(2, 500)), store.load())
        file.writeText("")
        assertEquals(emptyMap<Long, CardProgress>(), store.load())

        store.save(mapOf(1L to CardProgress(1, 1)), known = setOf(1L))
        folder.resolve(QuizProgressStore.FILE_NAME + ".tmp").mkdirs()
        assertEquals(false, store.save(mapOf(1L to CardProgress(5, 5)), known = setOf(1L)))
        assertEquals(mapOf(1L to CardProgress(1, 1)), store.load())
    }
}
