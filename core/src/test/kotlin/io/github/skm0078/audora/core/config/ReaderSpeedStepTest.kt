// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/ReaderSpeedStepTest.kt
// Purpose: "Audora, faster" and "slower" (plan item 38) move one offered speed, and stop at the ends.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSpeedStepTest {
    private val config = ReaderConfig()

    @Test
    fun `one step either way from an offered speed`() {
        assertEquals(1.25f, config.speedStep(1f, faster = true))
        assertEquals(0.75f, config.speedStep(1f, faster = false))
    }

    @Test
    fun `a speed between two offered ones moves to the next in that direction`() {
        assertEquals(1.5f, config.speedStep(1.3f, faster = true))
        assertEquals(1.25f, config.speedStep(1.3f, faster = false))
    }

    @Test
    fun `nothing past the ends`() {
        assertNull(config.speedStep(config.speeds.last(), faster = true))
        assertNull(config.speedStep(config.speeds.first(), faster = false))
    }
}
