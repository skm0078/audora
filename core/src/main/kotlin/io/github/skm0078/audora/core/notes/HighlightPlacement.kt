// File: core/src/main/kotlin/io/github/skm0078/audora/core/notes/HighlightPlacement.kt
// Purpose: Which of a page's current sentences each highlight covers, found again after the :pdf pipeline changed.

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ReAnchoring
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import kotlin.math.abs

/**
 * A highlight is saved on one sentence as the pipeline cut it then; a later pipeline may merge that sentence into its
 * neighbour (abbreviations and initials no longer end sentences), split it, or move every offset (a margin slug
 * dropped). Its sentence is found as resume finds a position ([ReAnchoring]), then checked against the saved quote:
 * a sentence that now holds the whole quote (merged) is covered alone, and the parts of a split sentence are covered
 * together, each part's text lying inside the quote in order.
 *
 * Resume accepts [AnchorMatch.OFFSET_UNVERIFIED] (at worst a little is read again); a highlight takes it only when the
 * quote confirms it, as with a short sentence merged into the next (both fingerprints then change). A band on a
 * sentence the listener never marked would be wrong, so a highlight whose text is gone is not shown; it stays in the
 * notes list with its quote.
 */
class HighlightPlacement(
    private val fingerprint: SentenceFingerprint = SentenceFingerprint(),
    private val reAnchoring: ReAnchoring = ReAnchoring(fingerprint),
) {
    /** [units] are one page's current units in reading order; the result maps each covered unit's charStart. */
    fun place(highlights: List<Highlight>, units: List<ReadingUnit>): Map<Int, Highlight> {
        val page = units.firstOrNull()?.page ?: return emptyMap()
        val texts = units.map { fingerprint.normalized(it.text) }
        val placed = LinkedHashMap<Int, Highlight>()
        highlights.filter { it.anchor.page == page }.forEach { highlight ->
            covered(highlight, units, texts)?.forEach { placed.putIfAbsent(units[it].charStart, highlight) }
        }
        return placed
    }

    private fun covered(highlight: Highlight, units: List<ReadingUnit>, texts: List<String>): IntRange? {
        val quote = fingerprint.normalized(highlight.quote)
        val point = reAnchoring.locate(highlight.anchor, highlight.anchor.pipelineVersion, units)
        val found = units.indexOfFirst { it.charStart == point.charStart }
        val verified = point.match != AnchorMatch.OFFSET_UNVERIFIED && point.match != AnchorMatch.PAGE_START
        return when {
            found < 0 -> null
            texts[found].contains(quote) -> found..found
            quote.contains(texts[found]) -> grown(found, quote, texts)
            verified -> found..found
            else -> null
        } ?: searched(highlight, quote, units, texts)
    }

    /** [found] and its neighbours while their texts, joined, still lie inside [quote]: a split sentence's parts. */
    private fun grown(found: Int, quote: String, texts: List<String>): IntRange {
        fun inQuote(first: Int, last: Int) = quote.contains((first..last).joinToString("") { texts[it] })
        var first = found
        var last = found
        while (first > 0 && inQuote(first - 1, last)) first--
        while (last < texts.lastIndex && inQuote(first, last + 1)) last++
        return first..last
    }

    /**
     * Short sentences have fingerprints of their whole text, so a merge or split changes both and [ReAnchoring] can
     * only guess by offset. The quote then finds them: a sentence holding all of it, or a run of sentences that make it
     * up exactly, the one nearest the saved offset.
     */
    private fun searched(
        highlight: Highlight,
        quote: String,
        units: List<ReadingUnit>,
        texts: List<String>,
    ): IntRange? {
        if (quote.isEmpty()) return null
        val runs = texts.indices.mapNotNull { first ->
            if (texts[first].contains(quote)) return@mapNotNull first..first
            var joined = ""
            (first..texts.lastIndex).firstNotNullOfOrNull { last ->
                joined += texts[last]
                when {
                    joined == quote -> first..last
                    quote.startsWith(joined) -> null
                    else -> IntRange.EMPTY
                }
            }?.takeUnless { it.isEmpty() }
        }
        return runs.minByOrNull { abs(units[it.first].charStart - highlight.anchor.charStart) }
    }
}
