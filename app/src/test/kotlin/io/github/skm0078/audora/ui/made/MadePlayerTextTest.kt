// File: app/src/test/kotlin/io/github/skm0078/audora/ui/made/MadePlayerTextTest.kt
// Purpose: The in-app player's clock reads as a player's does (ADR-068).

package io.github.skm0078.audora.ui.made

import org.junit.Assert.assertEquals
import org.junit.Test

class MadePlayerTextTest {
    @Test
    fun `the clock shows hours only when there are any`() {
        assertEquals("0:00", clockText(0))
        assertEquals("4:07", clockText(247_900))
        assertEquals("1:05:09", clockText(3_909_000))
        assertEquals("0:00", clockText(-5))
    }
}
