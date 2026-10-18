// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/HighlightPlacement.kt
// Purpose: Which of a page's current sentences each highlight covers, found again after the :pdf pipeline changed.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.config.HighlightConfig
import io.github.skm0078.audora.core.config.ResumeConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ReAnchoring
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import kotlin.math.abs

/**
 * A highlight is saved on one sentence as the pipeline cut it then; a later pipeline may merge that sentence into its
 * neighbour (abbreviations and initials no longer end sentences), split it, or move every offset (a margin slug
 * dropped). Placement never guesses: a band on a sentence the listener never marked would be wrong, and its Remove
 * would delete their highlight and note.
 *
 * 1. Found as resume finds a position ([ReAnchoring]), trusted at the saved offset, or by a fingerprint of part of a
 *    long sentence that is unique on the page (columns reordered), or else within [HighlightConfig.maxShiftChars] with
 *    no equally near rival.
 * 2. Otherwise by the saved quote, compared word by word: the same sentence, a sentence that begins or ends with it
 *    (merged), or neighbours that join into it exactly (split). One still at the saved place wins (an exact copy over
 *    the saved offset, a merge on the side the quote fills, a split at both ends); else the nearest of the first kind
 *    within [HighlightConfig.maxShiftChars], for quotes of at least [HighlightConfig.minQuoteChars]. A tie places
 *    nothing. A gone sentence with an identical copy within reach can still land on the copy: the text alone cannot
 *    tell them apart.
 *
 * A trusted sentence that the new pipeline split is covered with all its parts. A highlight whose text is gone is not
 * shown; it stays in the notes list with its quote.
 */
class HighlightPlacement(
    private val currentPipelineVersion: Int,
    private val config: HighlightConfig = HighlightConfig(),
    private val resume: ResumeConfig = ResumeConfig(),
) {
    private val fingerprint = SentenceFingerprint(resume)
    private val reAnchoring = ReAnchoring(fingerprint)

    /** [units] are one page's current units in reading order; each covered unit's charStart maps to its highlights. */
    fun place(highlights: List<Highlight>, units: List<ReadingUnit>): Map<Int, List<Highlight>> {
        val page = units.firstOrNull()?.page
        val onPage = highlights.filter { it.anchor.page == page }
        if (onPage.isEmpty()) return emptyMap()
        val text = PageText(units)
        val placed = LinkedHashMap<Int, MutableList<Highlight>>()
        onPage.forEach { highlight ->
            covered(highlight, text)?.forEach { placed.getOrPut(units[it].charStart, ::mutableListOf).add(highlight) }
        }
        return placed
    }

    /** One page's units as placement compares them, computed once per page. */
    private inner class PageText(val units: List<ReadingUnit>) {
        val words = units.map(::wordsOf)
        val heads = units.map { fingerprint.head(it.text) }
        val tails = units.map { fingerprint.tail(it.text) }

        /** [index] with the parts a split left of the sentence [quote] names; [index] alone if it holds it whole. */
        fun around(index: Int, quote: List<String>): IntRange {
            if (words[index].holds(quote)) return index..index
            return splits(quote).firstOrNull { index in it } ?: index..index
        }

        /** Runs of two or more units, none without words, whose words join into exactly [quote]. */
        fun splits(quote: List<String>): List<IntRange> = units.indices.mapNotNull { first ->
            var joined = emptyList<String>()
            var last = first
            while (last < units.size && words[last].isNotEmpty() && joined.size < quote.size) {
                joined = joined + words[last]
                last++
            }
            (first until last).takeIf { last - first > 1 && joined == quote }
        }

        /** [range] begins where [highlight] began. */
        fun startsAt(range: IntRange, highlight: Highlight): Boolean =
            units[range.first].charStart == highlight.anchor.charStart

        /** [range] ends where [highlight] ended, on the same page. */
        fun endsAt(range: IntRange, highlight: Highlight): Boolean =
            highlight.endPage == highlight.anchor.page && units[range.last].charEnd == highlight.endChar
    }

    private fun covered(highlight: Highlight, page: PageText): IntRange? {
        val quote = wordsOf(highlight.quote)
        if (quote.isEmpty()) return null
        val point = reAnchoring.locate(highlight.anchor, currentPipelineVersion, page.units)
        val found = page.units.indexOfFirst { it.charStart == point.charStart }
        val trusted = found >= 0 && when (point.match) {
            AnchorMatch.EXACT, AnchorMatch.OFFSET_VERIFIED -> true
            AnchorMatch.HEAD_FINGERPRINT -> fingerprintTrusted(highlight, quote, found, page.heads, page)
            AnchorMatch.TAIL_FINGERPRINT -> fingerprintTrusted(highlight, quote, found, page.tails, page)
            AnchorMatch.OFFSET_UNVERIFIED, AnchorMatch.PAGE_START -> false
        }
        return if (trusted) page.around(found, quote) else byQuote(highlight, quote, page)
    }

    /**
     * A fingerprint of part of a long sentence, unique on the page or near its saved offset with no equally near
     * rival. A short
     * sentence's fingerprints cover all of it ("Yes." matches every "Yes."), so it goes by its quote instead.
     */
    private fun fingerprintTrusted(
        highlight: Highlight,
        quote: List<String>,
        found: Int,
        fingerprints: List<Long>,
        page: PageText,
    ): Boolean {
        val same = fingerprints.indices.filter { fingerprints[it] == fingerprints[found] }
        val distance = distanceOf(found..found, highlight, page)
        val near =
            distance <= config.maxShiftChars && same.count { distanceOf(it..it, highlight, page) == distance } == 1
        return quote.sumOf { it.length } > resume.fingerprintChars && (same.size == 1 || near)
    }

    /**
     * A match that still sits where the highlight was wins whatever its kind (a merged sentence where the highlight was
     * beats an exact copy further down); several such place nothing. Otherwise the nearest of the first kind with any
     * match within [HighlightConfig.maxShiftChars]; two equally near place nothing rather than fall to the next kind.
     */
    private fun byQuote(highlight: Highlight, quote: List<String>, page: PageText): IntRange? {
        val long = quote.sumOf { it.length } >= config.minQuoteChars
        val exact = page.words.indices.filter { page.words[it] == quote }.map { it..it }
        val merged = page.words.indices.filter { page.words[it].size > quote.size && page.words[it].holds(quote) }
            .map { it..it }
        val splits = page.splits(quote)
        val covering = exact.filter { distanceOf(it, highlight, page) == 0 } +
            merged.filter { stays(it, highlight, page, quote) } +
            splits.filter { page.startsAt(it, highlight) && page.endsAt(it, highlight) }
        val inReach = listOf(exact, merged, splits).firstOrNull { candidates ->
            candidates.any { distanceOf(it, highlight, page) <= config.maxShiftChars }
        }
        return when {
            covering.isNotEmpty() -> covering.distinct().singleOrNull()
            long && inReach != null -> nearestUnique(inReach, highlight, page)
            else -> null
        }
    }

    /**
     * A merged sentence stays where the highlight was only on the side the quote fills: it begins at the saved start
     * and begins with the quote, or ends at the saved end and ends with it. Merely containing the saved offset is not
     * enough once offsets moved ("Thank you." before "Thank you for coming." after a margin slug was dropped).
     */
    private fun stays(range: IntRange, highlight: Highlight, page: PageText, quote: List<String>): Boolean {
        val words = page.words[range.first]
        val begins = words.subList(0, quote.size) == quote && page.startsAt(range, highlight)
        val ends = words.subList(words.size - quote.size, words.size) == quote && page.endsAt(range, highlight)
        return begins || ends
    }

    /** The candidate nearest the saved offset within the shift limit; null for none, or for a tie. */
    private fun nearestUnique(candidates: List<IntRange>, highlight: Highlight, page: PageText): IntRange? {
        val near = candidates.filter { distanceOf(it, highlight, page) <= config.maxShiftChars }
        val nearest = near.minOfOrNull { distanceOf(it, highlight, page) } ?: return null
        return near.filter { distanceOf(it, highlight, page) == nearest }.singleOrNull()
    }

    /** 0 when [range] covers the saved offset, else how far its start is from it. */
    private fun distanceOf(range: IntRange, highlight: Highlight, page: PageText): Int {
        val saved = highlight.anchor.charStart
        val start = page.units[range.first].charStart
        return if (saved in start until page.units[range.last].charEnd) 0 else abs(start - saved)
    }

    private fun wordsOf(unit: ReadingUnit): List<String> = wordsOf(unit.text)

    /** Letters, digits and marks of each word, as the fingerprints see them; words with none are dropped. */
    private fun wordsOf(text: String): List<String> =
        text.split(WHITESPACE).map(fingerprint::normalized).filter { it.isNotEmpty() }

    /** These words are [quote], or begin or end with all of it. */
    private fun List<String>.holds(quote: List<String>): Boolean =
        size >= quote.size && (subList(0, quote.size) == quote || subList(size - quote.size, size) == quote)

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
