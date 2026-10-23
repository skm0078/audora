// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/JumpConfig.kt
// Purpose: Tunables for the jump history and "Undo jump" (plan item 78).

package io.github.skm0078.audora.core.config

/**
 * @property mergeWindowMs jumps this close together are one jump: five headset skips in a row, or a pocket tapping
 *   the screen, undo in one tap back to where reading was before the first.
 * @property keep how many jumps are remembered; undoing walks back through them.
 */
data class JumpConfig(val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS, val keep: Int = DEFAULT_KEEP) {
    init {
        require(mergeWindowMs >= 0) { "mergeWindowMs must be >= 0, was $mergeWindowMs" }
        require(keep > 0) { "keep must be > 0, was $keep" }
    }

    private companion object {
        const val DEFAULT_MERGE_WINDOW_MS = 5_000L
        const val DEFAULT_KEEP = 20
    }
}
