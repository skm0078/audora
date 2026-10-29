// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/ListeningLogTest.kt
// Purpose: Slots listened add up, survive a new process, clear, old day-only files still load, and a foreign file is
//          set aside, never overwritten.

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
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
    fun `slots add up and survive a new process`() {
        val log = newProcess()
        log.add(mapOf(SLOT to Heard(1_000L, 10)))
        log.add(mapOf(SLOT to Heard(2_000L, 5), NEXT to Heard(500L)))

        assertEquals(mapOf(SLOT to Heard(3_000L, 15), NEXT to Heard(500L)), log.slots.value)
        assertEquals(log.slots.value, newProcess().slots.value)
    }

    @Test
    fun `adding before loading keeps what was saved`() {
        newProcess().add(mapOf(SLOT to Heard(1_000L)))
        val unloaded = ListeningLog(ListeningDaysStore(file))

        unloaded.add(mapOf(SLOT to Heard(1_000L)))

        assertEquals(mapOf(SLOT to Heard(2_000L)), newProcess().slots.value)
    }

    @Test
    fun `nothing to add writes nothing`() {
        newProcess().add(mapOf(SLOT to Heard()))
        assertFalse(file.exists())
    }

    @Test
    fun `clear forgets every day`() {
        val log = newProcess()
        log.add(mapOf(SLOT to Heard(1_000L)))
        log.clear()

        assertTrue(log.slots.value.isEmpty())
        assertTrue(newProcess().slots.value.isEmpty())
    }

    @Test
    fun `a foreign file is set aside and the next save starts fresh`() {
        file.writeText("something else\n")
        val log = newProcess()
        assertTrue(log.slots.value.isEmpty())

        log.add(mapOf(SLOT to Heard(1_000L)))

        assertEquals(mapOf(SLOT to Heard(1_000L)), newProcess().slots.value)
        assertEquals(1, dir.listFiles().orEmpty().count { it.name.contains("unreadable") })
    }

    @Test
    fun `a foreign file that will not move is never overwritten`() {
        file.writeText("something else\n")
        val store = ListeningDaysStore(file, moveAside = { false })
        store.load()

        assertFalse(store.save(mapOf(SLOT to Heard(1_000L))))
        assertEquals("something else\n", file.readText())
    }

    @Test
    fun `bad lines are dropped and good ones kept`() {
        newProcess().add(mapOf(SLOT to Heard(1_000L)))
        file.appendText(
            listOf(
                "2026-13-40\t5\t5\t0\t-\t-",
                "not a line",
                "2026-10-27\t24\t5\t0\t-\t-",
                "2026-10-27\t5\t-3\t0\t-\t-",
                "2026-10-27\t5\t0\t0\t-\t-",
            ).joinToString("\n", postfix = "\n"),
        )

        assertEquals(mapOf(SLOT to Heard(1_000L)), newProcess().slots.value)
    }

    @Test
    fun `a day-only file from before keeps its days, and saves as the new format`() {
        file.writeText("audora-listening-days 1\n2026-10-26\t60000\n2026-10-27\t0\nbad\n")
        val log = newProcess()

        assertEquals(mapOf(ListeningSlot(DAY) to Heard(60_000L)), log.slots.value)
        log.add(mapOf(SLOT to Heard(1_000L)))
        assertTrue(file.readText().startsWith("audora-listening-days 2\n"))
        assertEquals(mapOf(ListeningSlot(DAY) to Heard(60_000L), SLOT to Heard(1_000L)), newProcess().slots.value)
    }

    @Test
    fun `a slot with no book, language or hour round-trips`() {
        val bare = ListeningSlot(DAY, hour = null, book = null, lang = null)
        newProcess().add(mapOf(bare to Heard(1L, 2L), SLOT to Heard(3L)))

        assertEquals(mapOf(bare to Heard(1L, 2L), SLOT to Heard(3L)), newProcess().slots.value)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 26)
        val SLOT = ListeningSlot(DAY, 21, "book-1", Lang.BN)
        val NEXT = ListeningSlot(DAY.plusDays(1), 0, "book-1", Lang.HI)
    }
}
