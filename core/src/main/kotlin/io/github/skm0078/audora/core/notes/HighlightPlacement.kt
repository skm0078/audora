// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/HighlightPlacement.kt
// Purpose: Which of a page's current sentences each highlight covers, found again after the :pdf pipeline changed.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.config.HighlightConfig
import io.github.skm0078.audora.core.config.ResumeConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ReAnchoring
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import java.util.Collections
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
 *    nothing. A gone sentence, or a merged or split one whose offsets also moved, can land on an identical copy within
 *    reach: the text alone cannot tell that from a sentence that merely moved (a margin slug dropped).
 *
 * A trusted sentence that the new pipeline split is covered with all its parts. A highlight whose text is gone is not
 * shown; it stays in the notes list with its quote.
 *
 * A passage highlighted over several sentences (a range) is placed the same way, plus two rules of its own:
 * - On one page, at the pipeline it was saved with, it covers its first sentence through the one ending at its saved
 *   end, sentences without words included, as long as their words are still its quote.
 * - Across pages, each page is matched by its words alone: the start page's last sentences must begin the quote, the
 *   end page's first sentences must end it, and every page between must sit inside it. The match is the longest
 *   one, and one shorter than [HighlightConfig.minQuoteChars] counts only at the saved offset.
 */
class HighlightPlacement(
    private val currentPipelineVersion: Int,
    private val config: HighlightConfig = HighlightConfig(),
    private val resume: ResumeConfig = ResumeConfig(),
) {
    private val fingerprint = SentenceFingerprint(resume)
    private val reAnchoring = ReAnchoring(fingerprint)

    /**
     * [units] are one page's current units in reading order; each covered unit's index maps to its highlights (an
     * index, not charStart: a text-less table summary shares its offset with the row after it).
     */
    fun place(highlights: List<Highlight>, units: List<ReadingUnit>): Map<Int, List<Highlight>> {
        val page = units.firstOrNull()?.page
        val onPage = highlights.filter { page != null && page in it.anchor.page..it.endPage }
        if (onPage.isEmpty()) return emptyMap()
        val text = PageText(units)
        val placed = LinkedHashMap<Int, MutableList<Highlight>>()
        onPage.forEach { highlight ->
            val spansPages = highlight.endPage > highlight.anchor.page
            val range = if (spansPages) acrossPages(highlight, text) else covered(highlight, text)
            range?.forEach { placed.getOrPut(units[it].index, ::mutableListOf).add(highlight) }
        }
        return placed
    }

    /** One page's units as placement compares them, computed once per page. */
    private inner class PageText(val units: List<ReadingUnit>) {
        val words = units.map(::wordsOf)
        val heads = units.map { fingerprint.head(it) }
        val tails = units.map { fingerprint.tail(it) }

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

        /** The words of [range]'s units, in order; units without words add none. */
        fun wordsIn(range: IntRange): List<String> = range.flatMap { words[it] }
    }

    private fun covered(highlight: Highlight, page: PageText): IntRange? {
        val quote = wordsOf(highlight.quote)
        val point = reAnchoring.locate(highlight.anchor, currentPipelineVersion, page.units)
        // Units can share an offset (a text-less table summary and its first row): the one with the saved fingerprint.
        val atPoint = page.units.indices.filter { page.units[it].charStart == point.charStart }
        val found = atPoint.firstOrNull { page.heads[it] == highlight.anchor.fingerprint } ?: atPoint.lastOrNull() ?: -1
        if (quote.isEmpty()) {
            // A text-less unit (anchorText) is placed only where ReAnchoring verified it at its offset.
            val atOffset = point.match == AnchorMatch.EXACT || point.match == AnchorMatch.OFFSET_VERIFIED
            return (found..found).takeIf { found >= 0 && atOffset }
        }
        val trusted = found >= 0 && when (point.match) {
            AnchorMatch.EXACT, AnchorMatch.OFFSET_VERIFIED -> true
            AnchorMatch.HEAD_FINGERPRINT -> fingerprintTrusted(highlight, quote, found, page.heads, page)
            AnchorMatch.TAIL_FINGERPRINT -> fingerprintTrusted(highlight, quote, found, page.tails, page)
            AnchorMatch.OFFSET_UNVERIFIED, AnchorMatch.PAGE_START -> false
        }
        val exact = found >= 0 && point.match == AnchorMatch.EXACT
        val passage = if (exact) exactPassage(highlight, quote, found, page) else null
        return passage ?: if (trusted) page.around(found, quote) else byQuote(highlight, quote, page)
    }

    /**
     * At the pipeline it was saved with: [found] through the unit ending at the saved end, when their words are still
     * the quote. A range over a sentence without words ("* * *") is found only this way: a split never joins one.
     */
    private fun exactPassage(highlight: Highlight, quote: List<String>, found: Int, page: PageText): IntRange? {
        val last = (found until page.units.size).firstOrNull { page.units[it].charEnd == highlight.endChar }
        return last?.let { found..it }?.takeIf { page.wordsIn(it) == quote }
    }

    /** One page's share of a passage that runs over several pages; null when the page no longer holds its part. */
    private fun acrossPages(highlight: Highlight, page: PageText): IntRange? {
        val quote = wordsOf(highlight.quote)
        val units = page.units
        if (quote.isEmpty()) return null
        return when (units.first().page) {
            highlight.anchor.page -> passageStart(highlight, quote, page)?.let { it..units.lastIndex }
            highlight.endPage -> passageEnd(highlight, quote, page)?.let { 0..it }
            else -> units.indices.takeIf { Collections.indexOfSubList(quote, page.wordsIn(it)) >= 0 }
        }
    }

    /** The first unit of the longest run of the page's last sentences that begins [quote]. */
    private fun passageStart(highlight: Highlight, quote: List<String>, page: PageText): Int? {
        val last = page.units.lastIndex
        val first = page.units.indices.firstOrNull { index ->
            val run = page.wordsIn(index..last)
            page.words[index].isNotEmpty() && run.size <= quote.size && quote.subList(0, run.size) == run
        } ?: return null
        return first.takeIf {
            enough(page.wordsIn(first..last)) ||
                page.units[it].charStart == highlight.anchor.charStart
        }
    }

    /** The last unit of the longest run of the page's first sentences that ends [quote]. */
    private fun passageEnd(highlight: Highlight, quote: List<String>, page: PageText): Int? {
        val last = page.units.indices.lastOrNull { index ->
            val run = page.wordsIn(0..index)
            page.words[index].isNotEmpty() && run.size <= quote.size &&
                quote.subList(quote.size - run.size, quote.size) == run
        } ?: return null
        return last.takeIf { enough(page.wordsIn(0..last)) || page.units[it].charEnd == highlight.endChar }
    }

    private fun enough(words: List<String>) = words.sumOf { it.length } >= config.minQuoteChars

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
     * A match that still sits where the highlight was wins whatever its kind: a merged sentence beats an exact copy
     * further down only while the quote's side has not moved; several such place nothing. Otherwise the nearest of the
     * first kind with any match within [HighlightConfig.maxShiftChars]; two equally near place nothing rather than fall
     * to the next kind.
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
