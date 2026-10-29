// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/StartupConfig.kt
// Purpose: Tunables for the app's first screen (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.core.config

/**
 * @property appearanceWaitMs the longest the first frame waits for the saved look (dark mode, colours) before it is
 *   drawn in the default look. Reading it takes a few tens of milliseconds on a cold start; this only bounds a slow
 *   or failing read, so the app never sits on its splash screen.
 */
data class StartupConfig(val appearanceWaitMs: Long = DEFAULT_APPEARANCE_WAIT_MS) {
    init {
        require(appearanceWaitMs >= 0) { "appearanceWaitMs must be >= 0, was $appearanceWaitMs" }
    }

    private companion object {
        const val DEFAULT_APPEARANCE_WAIT_MS = 300L
    }
}
