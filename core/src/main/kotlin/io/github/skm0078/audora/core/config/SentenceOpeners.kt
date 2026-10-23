// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SentenceOpeners.kt
// Purpose: Capitalised words that usually open a sentence, so a full stop before them is a real sentence end.

package io.github.skm0078.audora.core.config

/**
 * Words that, capitalised after a full stop, almost always start a new sentence: "in the U.S. The economy",
 * "Malcolm X. The man". The speech normalizer's initials rule (:speech) keeps such a stop instead of reading it as
 * the dot of an initial, so the voice still falls there. One list in :core, as [NumberingWords] is, so :pdf can read
 * the same words when it needs them.
 */
object SentenceOpeners {
    val ALL: Set<String> = setOf(
        "A", "After", "An", "And", "As", "At", "Before", "But", "He", "Her", "His", "I", "If", "In", "It", "Its",
        "My", "No", "Not", "On", "Our", "She", "So", "That", "The", "Their", "Then", "There", "These", "They", "This",
        "Those", "We", "When", "While", "You",
    )
}
