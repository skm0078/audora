// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ActionSearchConfig.kt
// Purpose: Tunables for the library search's actions and settings (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.core.config

/**
 * @property minQueryLength fewer letters than this offer no actions: one letter would match most of them.
 * @property maxResults rows above the books, so the books stay in view on a phone.
 */
data class ActionSearchConfig(
    val minQueryLength: Int = DEFAULT_MIN_QUERY_LENGTH,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
) {
    init {
        require(minQueryLength >= 1) { "minQueryLength must be at least 1, was $minQueryLength" }
        require(maxResults >= 1) { "maxResults must be at least 1, was $maxResults" }
    }

    private companion object {
        const val DEFAULT_MIN_QUERY_LENGTH = 2
        const val DEFAULT_MAX_RESULTS = 5
    }
}
