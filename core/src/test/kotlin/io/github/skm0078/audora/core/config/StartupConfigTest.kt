// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/StartupConfigTest.kt
// Purpose: The first frame's hold always ends after the wait for the saved look, never before it
//          (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertTrue
import org.junit.Test

class StartupConfigTest {
    @Test
    fun `by default the hold outlasts the wait for the saved look`() {
        val config = StartupConfig()
        assertTrue(config.firstFrameMaxHoldMs > config.appearanceWaitMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a hold shorter than the wait is refused`() {
        StartupConfig(appearanceWaitMs = 300L, firstFrameMaxHoldMs = 200L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative wait is refused`() {
        StartupConfig(appearanceWaitMs = -1L)
    }
}
