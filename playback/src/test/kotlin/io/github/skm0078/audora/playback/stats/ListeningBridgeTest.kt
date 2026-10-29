// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/stats/ListeningBridgeTest.kt
// Purpose: The service's reading reports reach the listening log by hour, book and language: saved on each page change
//          and on stop, with the words of the sentences heard (CF-8, stats screen).

package io.github.skm0078.audora.playback.stats

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.stats.Heard
import io.github.skm0078.audora.core.stats.ListeningClock
import io.github.skm0078.audora.core.stats.ListeningDaysStore
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.core.stats.ListeningSlot
import io.github.skm0078.audora.core.stats.ListeningTally
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningBridgeTest {
    private val dir = Files.createTempDirectory("listening").toFile()
    private val log = ListeningLog(ListeningDaysStore(File(dir, ListeningDaysStore.FILE_NAME)))
    private val clock = StepClock()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `listening is saved at each page change and at the stop`() = runTest(UnconfinedTestDispatcher()) {
        val io = UnconfinedTestDispatcher(testScheduler)
        val bridge = ListeningBridge(log, backgroundScope, ListeningTally(clock), io)

        bridge.reading(BOOK)
        bridge.sentence(BOOK, Lang.EN, "Once upon a time,")
        clock.advance(MINUTE * 3)
        assertEquals("a sentence alone does not save", emptyMap<ListeningSlot, Heard>(), log.slots.value)
        bridge.reading(BOOK) // next page
        assertEquals(mapOf(SLOT to Heard(MINUTE * 3, 4)), log.slots.value)

        clock.advance(MINUTE * 2)
        bridge.notReading()
        clock.advance(MINUTE * 30) // paused
        bridge.notReading()

        assertEquals(mapOf(SLOT to Heard(MINUTE * 5, 4)), log.slots.value)
        val reopened = ListeningLog(ListeningDaysStore(File(dir, ListeningDaysStore.FILE_NAME))).apply { load() }
        assertEquals("saved, not only in memory", log.slots.value, reopened.slots.value)
    }

    private class StepClock : ListeningClock {
        private val zone = ZoneId.of("Asia/Kolkata")
        private var elapsed = 0L
        private var wall = LocalDateTime.of(2026, 10, 26, 9, 0).atZone(zone).toInstant().toEpochMilli()

        fun advance(ms: Long) {
            elapsed += ms
            wall += ms
        }

        override fun elapsedMillis() = elapsed

        override fun wallMillis() = wall

        override fun zone(): ZoneId = zone
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 26)
        const val BOOK = "book-1"
        val SLOT = ListeningSlot(DAY, 9, BOOK, Lang.EN)
        const val MINUTE = 60_000L
    }
}
