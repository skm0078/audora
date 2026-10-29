// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderVoiceWarmUpTest.kt
// Purpose: The voice is asked for once, only after the book stayed open, and never while something plays
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
    private var quiet = true

    private fun TestScope.warmUp() = ReaderVoiceWarmUp(backgroundScope, DELAY_MS, { quiet }) { warmed += it }

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
    fun `later pages and reconnects ask nothing more`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)
        warmUp.offer(Lang.HI)
        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)

        assertEquals(listOf(Lang.EN), warmed)
    }

    @Test
    fun `a book closed before the wait ends loads nothing`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        warmUp.cancel()
        waitPast(DELAY_MS)

        assertEquals(emptyList<Lang>(), warmed)
    }

    @Test
    fun `nothing is loaded while something plays, and a later offer tries again`() = runTest {
        val warmUp = warmUp()
        warmUp.offer(Lang.EN)
        quiet = false
        waitPast(DELAY_MS)
        assertEquals(emptyList<Lang>(), warmed)

        warmUp.offer(Lang.EN) // still playing: not even scheduled
        quiet = true
        waitPast(DELAY_MS)
        assertEquals(emptyList<Lang>(), warmed)

        warmUp.offer(Lang.EN)
        waitPast(DELAY_MS)
        assertEquals(listOf(Lang.EN), warmed)
    }

    @Test
    fun `a page whose sentences are not known yet asks nothing`() = runTest {
        warmUp().offer(null)
        waitPast(DELAY_MS)

        assertEquals(emptyList<Lang>(), warmed)
    }

    private companion object {
        const val DELAY_MS = 1_000L
    }
}
