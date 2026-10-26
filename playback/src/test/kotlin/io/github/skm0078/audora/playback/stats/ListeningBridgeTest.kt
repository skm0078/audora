// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/stats/ListeningBridgeTest.kt
// Purpose: The service's reading reports reach the listening log: saved on each page change and on stop (CF-8).

package io.github.skm0078.audora.playback.stats

import io.github.skm0078.audora.core.stats.ListeningClock
import io.github.skm0078.audora.core.stats.ListeningDaysStore
import io.github.skm0078.audora.core.stats.ListeningLog
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

        bridge.reading()
        clock.advance(MINUTE * 3)
        bridge.reading() // next page
        assertEquals(mapOf(DAY to MINUTE * 3), log.days.value)

        clock.advance(MINUTE * 2)
        bridge.notReading()
        clock.advance(MINUTE * 30) // paused
        bridge.notReading()

        assertEquals(mapOf(DAY to MINUTE * 5), log.days.value)
        val reopened = ListeningLog(ListeningDaysStore(File(dir, ListeningDaysStore.FILE_NAME))).apply { load() }
        assertEquals("saved, not only in memory", log.days.value, reopened.days.value)
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
        const val MINUTE = 60_000L
    }
}
