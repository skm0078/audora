// File: core/src/test/kotlin/io/github/skm0078/audora/core/hints/FirstUseHintsTest.kt
// Purpose: The first-use hints show once each, one per app start, never when muted, most useful first.

package io.github.skm0078.audora.core.hints

import io.github.skm0078.audora.core.config.FirstUseHintsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstUseHintsTest {
    private val all = ReaderHintFacts(
        cloudBook = true,
        fastWithoutSmartSpeed = true,
        storyWithoutCast = true,
        hasHighlight = true,
        onLastPage = true,
        longHalfReadWhileCharging = true,
    )

    @Test
    fun `the facts list their hints most useful first`() {
        val expected = listOf(
            FirstUseHint.KEEP_OFFLINE,
            FirstUseHint.SMART_SPEED,
            FirstUseHint.CAST,
            FirstUseHint.QUIZ,
            FirstUseHint.FINISHED,
            FirstUseHint.PREPARE_AHEAD,
        )
        assertEquals(expected, all.candidates())
        assertEquals(emptyList<FirstUseHint>(), ReaderHintFacts().candidates())
    }

    @Test
    fun `the first hint not seen shows`() {
        val seen = setOf(FirstUseHint.KEEP_OFFLINE, FirstUseHint.SMART_SPEED)
        assertEquals(FirstUseHint.CAST, pickHint(all.candidates(), HintHistory(seen = seen)))
    }

    @Test
    fun `a seen hint never shows again`() {
        val candidates = listOf(FirstUseHint.QUIZ)
        assertNull(pickHint(candidates, HintHistory(seen = setOf(FirstUseHint.QUIZ))))
    }

    @Test
    fun `one hint an app start`() {
        val history = HintHistory(shownThisStart = true)
        assertNull(pickHint(listOf(FirstUseHint.QUIZ), history))
    }

    @Test
    fun `the hint showing stays while it applies and goes when it does not`() {
        val history = HintHistory(shownThisStart = true, showing = FirstUseHint.QUIZ)
        assertEquals(FirstUseHint.QUIZ, pickHint(listOf(FirstUseHint.CAST, FirstUseHint.QUIZ), history))
        assertNull(pickHint(listOf(FirstUseHint.CAST), history))
    }

    @Test
    fun `muted shows nothing, even the hint showing`() {
        val history = HintHistory(muted = true, showing = FirstUseHint.QUIZ)
        assertNull(pickHint(listOf(FirstUseHint.QUIZ), history))
    }

    @Test
    fun `keys round trip and an unknown key is ignored`() {
        FirstUseHint.entries.forEach { assertEquals(it, FirstUseHint.fromKey(it.key)) }
        assertNull(FirstUseHint.fromKey("gone"))
    }

    @Test
    fun `a long book is half read between a quarter and three quarters`() {
        val config = FirstUseHintsConfig()
        assertTrue(config.isLongAndHalfRead(page = 99, pageCount = 200))
        assertFalse(config.isLongAndHalfRead(page = 10, pageCount = 200))
        assertFalse(config.isLongAndHalfRead(page = 180, pageCount = 200))
        assertFalse(config.isLongAndHalfRead(page = 20, pageCount = 40))
    }
}
