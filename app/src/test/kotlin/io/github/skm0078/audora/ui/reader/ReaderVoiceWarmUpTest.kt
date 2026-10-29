// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderVoiceWarmUpTest.kt
// Purpose: The voice is asked for once, only after the book stayed open and quiet, and never after it closed
//          (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderVoiceWarmUpTest {
    private val warmed = mutableListOf<Lang>()

    private fun TestScope.warmUp() = ReaderVoiceWarmUp(backgroundScope, DELAY_MS) { warmed += it }

    private fun TestScope.waitPast(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `the voice is asked for once the book has stayed open`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS - 1)
        assertEquals(emptyList<Lang>(), warmed)

        waitPast(1)
        assertEquals(listOf(Lang.EN), warmed)
    }

    @Test
    fun `once asked, later pages and pauses ask nothing more`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)
        warmUp.offer(null)
        warmUp.offer(Lang.HI)
        waitPast(DELAY_MS)

        assertEquals(listOf(Lang.EN), warmed)
    }

    @Test
    fun `after a lost connection the next one asks again`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)
        warmUp.offer(null)
        warmUp.rearm()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)

        assertEquals(listOf(Lang.EN, Lang.EN), warmed)
    }

    @Test
    fun `a closed book is not asked for again by a later connection`() = runTest {
        val warmUp = warmUp()
        warmUp.stop()
        warmUp.rearm()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)

        assertEquals(emptyList<Lang>(), warmed)
    }

    @Test
    fun `playing before the wait ends drops it, and quiet again starts a new one`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS / 2)
        warmUp.offer(null)
        waitPast(DELAY_MS)
        assertEquals(emptyList<Lang>(), warmed)

        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)
        assertEquals(listOf(Lang.EN), warmed)
    }

    @Test
    fun `a page in another language before the wait ends asks for that language`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS / 2)
        warmUp.offer(Lang.BN)
        waitPast(DELAY_MS)

        assertEquals(listOf(Lang.BN), warmed)
    }

    @Test
    fun `a book closed before the wait ends loads nothing, then or later`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        warmUp.stop()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)

        assertEquals(emptyList<Lang>(), warmed)
    }

    private companion object {
        const val DELAY_MS = 1_000L
    }
}
