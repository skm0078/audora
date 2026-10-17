// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/TimeLeftConfig.kt
// Purpose: Tunables for "about 2 h 10 min left at 1.25×" (plan item 24): how fast each language is spoken.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.Lang

/**
 * @property charsPerSecond characters of text (spaces included) a voice speaks per second at 1×, by language. English
 *   neural voices speak about 150 words a minute, close to 15 characters a second; the Hindi and Bengali voices are a
 *   little slower per character (longer syllables per letter). Tuned against measured voices in the final session.
 * @property samplePages how many pages the estimate remembers the text of (the pages the reader has shown); pages it
 *   has not seen count as the average of those.
 */
data class TimeLeftConfig(
    val charsPerSecond: Map<Lang, Float> = DEFAULT_CHARS_PER_SECOND,
    val samplePages: Int = DEFAULT_SAMPLE_PAGES,
) {
    init {
        require(charsPerSecond.keys == Lang.entries.toSet()) { "every language needs a speaking rate" }
        require(charsPerSecond.values.all { it > 0f }) { "speaking rates must be positive, were $charsPerSecond" }
        require(samplePages > 0) { "samplePages must be > 0, was $samplePages" }
    }

    private companion object {
        val DEFAULT_CHARS_PER_SECOND = mapOf(Lang.EN to 15f, Lang.HI to 13f, Lang.BN to 13f)
        const val DEFAULT_SAMPLE_PAGES = 64
    }
}
