// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SpeechConfigTest.kt
// Purpose: The Bengali letter voices' pace defaults to what a native listener chose, and a broken pace is refused.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeechConfigTest {
    @Test
    fun `Bengali letter voices speak at 0_9 of the asked pace by default`() {
        assertEquals(0.9f, SpeechConfig().bengaliPace)
    }

    @Test
    fun `a pace of zero, below or not a number is refused`() {
        listOf(0f, -0.5f, Float.NaN, Float.POSITIVE_INFINITY).forEach { pace ->
            assertThrows("pace $pace", IllegalArgumentException::class.java) { SpeechConfig(bengaliPace = pace) }
        }
    }
}
