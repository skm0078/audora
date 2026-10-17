// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DialogueConfig.kt
// Purpose: Tunables for reading quoted speech in a second voice (plan item 7).

package io.github.skm0078.audora.core.config

/**
 * @property minQuoteWords the fewest words a quote needs to count as speech: shorter ones ("so-called", a quoted
 *   title of two words) stay in the narrator's voice, since a voice switch for one word sounds like a glitch.
 */
data class DialogueConfig(val minQuoteWords: Int = 3) {
    init {
        require(minQuoteWords >= 1) { "minQuoteWords must be >= 1, was $minQuoteWords" }
    }
}
