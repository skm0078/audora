// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DictionaryConfig.kt
// Purpose: Tunables for looking words up in the downloaded dictionary (plan item 33, ADR-045).

package io.github.skm0078.audora.core.config

/**
 * @property maxEntries a look-up shows at most this many headwords (the word itself, then its base forms: "leaves"
 *   gives "leave" and "leaf").
 * @property maxLineBytes a dictionary line longer than this is treated as damage and not read; the longest real line
 *   ("run", "set") is about 10 KB.
 * @property chunkBytes the file is read in blocks of this size while searching.
 * @property maxWordChars a tapped word longer than this is not looked up (no headword is that long).
 * @property cancelWaitMs deleting a dictionary waits at most this long for a cancelled download to stop first.
 */
data class DictionaryConfig(
    val maxEntries: Int = 3,
    val maxLineBytes: Int = 65_536,
    val chunkBytes: Int = 4_096,
    val maxWordChars: Int = 64,
    val cancelWaitMs: Long = 10_000L,
) {
    init {
        require(maxEntries >= 1) { "maxEntries must be >= 1, was $maxEntries" }
        require(maxLineBytes >= chunkBytes) { "maxLineBytes ($maxLineBytes) must be >= chunkBytes ($chunkBytes)" }
        require(chunkBytes > 0) { "chunkBytes must be > 0, was $chunkBytes" }
        require(maxWordChars > 0) { "maxWordChars must be > 0, was $maxWordChars" }
        require(cancelWaitMs >= 0) { "cancelWaitMs must be >= 0, was $cancelWaitMs" }
    }
}
