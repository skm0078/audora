// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningLogTest.kt
// Purpose: Days listened add up, survive a new process, clear, and a foreign file is set aside, never overwritten.

package io.github.skm0078.audora.core.stats

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningLogTest {
    private val dir = Files.createTempDirectory("listening").toFile()
    private val file = File(dir, ListeningDaysStore.FILE_NAME)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun newProcess() = ListeningLog(ListeningDaysStore(file)).apply { load() }

    @Test
    fun `days add up and survive a new process`() {
        val log = newProcess()
        log.add(mapOf(DAY to 1_000L))
        log.add(mapOf(DAY to 2_000L, DAY.plusDays(1) to 500L))

        assertEquals(mapOf(DAY to 3_000L, DAY.plusDays(1) to 500L), log.days.value)
        assertEquals(log.days.value, newProcess().days.value)
    }

    @Test
    fun `adding before loading keeps what was saved`() {
        newProcess().add(mapOf(DAY to 1_000L))
        val unloaded = ListeningLog(ListeningDaysStore(file))

        unloaded.add(mapOf(DAY to 1_000L))

        assertEquals(mapOf(DAY to 2_000L), newProcess().days.value)
    }

    @Test
    fun `nothing to add writes nothing`() {
        newProcess().add(mapOf(DAY to 0L))
        assertFalse(file.exists())
    }

    @Test
    fun `clear forgets every day`() {
        val log = newProcess()
        log.add(mapOf(DAY to 1_000L))
        log.clear()

        assertTrue(log.days.value.isEmpty())
        assertTrue(newProcess().days.value.isEmpty())
    }

    @Test
    fun `a foreign file is set aside and the next save starts fresh`() {
        file.writeText("something else\n")
        val log = newProcess()
        assertTrue(log.days.value.isEmpty())

        log.add(mapOf(DAY to 1_000L))

        assertEquals(mapOf(DAY to 1_000L), newProcess().days.value)
        assertEquals(1, dir.listFiles().orEmpty().count { it.name.contains("unreadable") })
    }

    @Test
    fun `a foreign file that will not move is never overwritten`() {
        file.writeText("something else\n")
        val store = ListeningDaysStore(file, moveAside = { false })
        store.load()

        assertFalse(store.save(mapOf(DAY to 1_000L)))
        assertEquals("something else\n", file.readText())
    }

    @Test
    fun `bad lines are dropped and good ones kept`() {
        newProcess().add(mapOf(DAY to 1_000L))
        file.appendText("2026-13-40\t5\nnot a line\n2026-10-27\t-3\n")

        assertEquals(mapOf(DAY to 1_000L), newProcess().days.value)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 26)
    }
}
