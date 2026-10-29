// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/StartupConfig.kt
// Purpose: Tunables for the app's first screen (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.core.config

/**
 * @property appearanceWaitMs the longest the first frame waits for the saved look (dark mode, colours) before it is
 *   drawn in the default look. Reading it takes a few tens of milliseconds on a cold start; this only bounds a slow
 *   or failing read, so the app never sits on its splash screen.
 * @property firstFrameMaxHoldMs the longest the first frame is held at all, measured by the main thread's clock. It
 *   ends the hold even if the first screen was never composed (a UI test's paused clock, a stalled composition);
 *   longer than [appearanceWaitMs] so that, normally, the screen is composed first.
 */
data class StartupConfig(
    val appearanceWaitMs: Long = DEFAULT_APPEARANCE_WAIT_MS,
    val firstFrameMaxHoldMs: Long = DEFAULT_FIRST_FRAME_MAX_HOLD_MS,
) {
    init {
        require(appearanceWaitMs >= 0) { "appearanceWaitMs must be >= 0, was $appearanceWaitMs" }
        require(firstFrameMaxHoldMs >= appearanceWaitMs) {
            "firstFrameMaxHoldMs ($firstFrameMaxHoldMs) must be >= appearanceWaitMs ($appearanceWaitMs)"
        }
    }

    private companion object {
        const val DEFAULT_APPEARANCE_WAIT_MS = 300L
        const val DEFAULT_FIRST_FRAME_MAX_HOLD_MS = 600L
    }
}
