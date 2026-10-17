// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DialogueConfig.kt
// Purpose: Tunables for reading quoted speech in a second voice and in each character's voice (plan items 7, 63).

package io.github.skm0078.audora.core.config

/**
 * @property minQuoteWords the fewest words a quote needs to count as speech: shorter ones ("so-called", a quoted
 *   title of two words) stay in the narrator's voice, since a voice switch for one word sounds like a glitch.
 * @property rememberedPages pages whose open quotes are kept for the renderer: the playlist's pages ahead and a few
 *   behind for a skip back; older pages are forgotten, and their sentences start outside any quote.
 * @property rememberedSpeakers named characters kept for a pronoun to point back to ("she said" after Mira spoke):
 *   a scene rarely has more talking at once, and an older name is more likely a wrong guess than a right one.
 */
data class DialogueConfig(val minQuoteWords: Int = 3, val rememberedPages: Int = 8, val rememberedSpeakers: Int = 6) {
    init {
        require(rememberedSpeakers >= 1) { "rememberedSpeakers must be >= 1, was $rememberedSpeakers" }
        require(minQuoteWords >= 1) { "minQuoteWords must be >= 1, was $minQuoteWords" }
        require(rememberedPages >= 1) { "rememberedPages must be >= 1, was $rememberedPages" }
    }
}
