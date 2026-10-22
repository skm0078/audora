// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/CharacterGuideConfig.kt
// Purpose: Limits for the character guide, the listener's own spoiler-free list of who is who (plan item 80).

package io.github.skm0078.audora.core.config

/**
 * @property maxNameChars a longer name is cut.
 * @property maxNoteChars a longer note is cut.
 * @property maxEntries the most characters one book keeps; adding more is refused.
 */
data class CharacterGuideConfig(val maxNameChars: Int = 80, val maxNoteChars: Int = 600, val maxEntries: Int = 500) {
    init {
        require(maxNameChars > 0) { "maxNameChars must be > 0, was $maxNameChars" }
        require(maxNoteChars >= 0) { "maxNoteChars must be >= 0, was $maxNoteChars" }
        require(maxEntries > 0) { "maxEntries must be > 0, was $maxEntries" }
    }
}
