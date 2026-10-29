// File: core/src/main/kotlin/io/github/skm0078/audora/core/home/LineOfTheDay.kt
// Purpose: Picks one of the listener's own highlights for the day, the same one all day (personal touch 15).

package io.github.skm0078.audora.core.home

import io.github.skm0078.audora.core.notes.Highlight

/**
 * The listener's own words back to them: one highlight a day, from every book, chosen by the day alone so the card
 * does not change while they look and needs nothing stored. Never shown on the lock screen or in a notification.
 */
object LineOfTheDay {
    /** Longer quotes are cut at a word, so the card keeps its height. */
    const val QUOTE_MAX = 200

    /**
     * The highlight for [epochDay] (days since 1970-01-01, local), or null when there is none with words. Ordered by
     * id, so the pick stays put all day unless the listener adds or removes a highlight.
     */
    fun pick(highlights: List<Highlight>, epochDay: Long): Highlight? {
        val candidates = highlights.filter { it.quote.isNotBlank() }.sortedBy { it.id }
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(epochDay * SPREAD, candidates.size.toLong()).toInt()]
    }

    /** [quote] on one line, cut at the last space before [max] with an ellipsis. */
    fun clip(quote: String, max: Int = QUOTE_MAX): String {
        val line = quote.replace(WHITESPACE, " ").trim()
        if (line.length <= max) return line
        val cut = line.lastIndexOf(' ', max - 1).takeIf { it > max / 2 } ?: (max - 1)
        return line.take(cut).trimEnd() + ELLIPSIS
    }

    /** A prime, so neighbouring days land far apart in a long list rather than on neighbouring highlights. */
    private const val SPREAD = 7919L
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")
}
