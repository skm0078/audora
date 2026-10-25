// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/StoryBrainConfig.kt
// Purpose: Tunables for the opt-in story brain (ADR-078): which phones run it, how far ahead it reads, its budget.

package io.github.skm0078.audora.core.config

/**
 * The brain reads a story's pages ahead of the listener, one page per question, and only pages with speech on them.
 *
 * @property minMemoryBytes phones with less memory are not offered it: the model maps 2.7 GB and the app, the voice
 *   and Android need the rest (measured headroom on 6 GB phones; 8 GB is comfortable).
 * @property contextTokens the model's context: a page of a novel is 400 to 900 tokens, the instructions about 350.
 * @property threads model threads; two leave the big cores the voice needs to keep up (the voice comes first).
 * @property pagesAhead pages read ahead of the one playing; a page takes the brain well under the minutes it plays.
 * @property earlierSentences sentences of the page before, given for a conversation that runs over the page break.
 * @property maxCast speakers carried from page to page, the most recent first, so names stay the same.
 * @property maxPageChars a page longer than this is cut into parts, so a question always fits [contextTokens].
 */
data class StoryBrainConfig(
    val minMemoryBytes: Long = DEFAULT_MIN_MEMORY_BYTES,
    val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
    val threads: Int = DEFAULT_THREADS,
    val pagesAhead: Int = DEFAULT_PAGES_AHEAD,
    val earlierSentences: Int = DEFAULT_EARLIER_SENTENCES,
    val maxCast: Int = DEFAULT_MAX_CAST,
    val maxPageChars: Int = DEFAULT_MAX_PAGE_CHARS,
) {
    init {
        require(minMemoryBytes > 0) { "minMemoryBytes must be > 0, was $minMemoryBytes" }
        require(contextTokens >= MIN_CONTEXT_TOKENS) {
            "contextTokens must be >= $MIN_CONTEXT_TOKENS, was $contextTokens"
        }
        require(threads > 0) { "threads must be > 0, was $threads" }
        require(pagesAhead >= 0) { "pagesAhead must be >= 0, was $pagesAhead" }
        require(earlierSentences >= 0) { "earlierSentences must be >= 0, was $earlierSentences" }
        require(maxCast >= 0) { "maxCast must be >= 0, was $maxCast" }
        require(maxPageChars > 0) { "maxPageChars must be > 0, was $maxPageChars" }
    }

    private companion object {
        /** 6 GB phones report a little under 6 GB of total memory. */
        const val DEFAULT_MIN_MEMORY_BYTES = 5_500_000_000L
        const val DEFAULT_CONTEXT_TOKENS = 4096
        const val MIN_CONTEXT_TOKENS = 1024
        const val DEFAULT_THREADS = 2
        const val DEFAULT_PAGES_AHEAD = 2
        const val DEFAULT_EARLIER_SENTENCES = 4
        const val DEFAULT_MAX_CAST = 12
        const val DEFAULT_MAX_PAGE_CHARS = 6000
    }
}
