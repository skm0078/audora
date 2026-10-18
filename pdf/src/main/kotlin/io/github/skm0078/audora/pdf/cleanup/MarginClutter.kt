// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/MarginClutter.kt
// Purpose: Finds running headers, footers and page numbers by comparing a page's margin lines with its neighbours'.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.layout.TextLine

/** Why a margin line was dropped; counted in the page's cleanup log line. */
internal enum class MarginReason { REPEATED, RUNNING_FOLIO, STANDALONE_FOLIO, PRINT_SLUG }

/**
 * Spec §5 step 5, ADR-020. A line in the top or bottom band of the text area is clutter when:
 * - it repeats (same signature, same band) on at least [CleanupConfig.minRepeatPages] pages of the window, and
 *   no line in the page's body reads the same (numbered, templated lines are text);
 * - it carries a page number that runs with the page index on another page of the window ("31" on page 40 and
 *   "Page 3 of 50" beside "Page 2 of 50" on the page before), which catches headers whose text changes by chapter;
 * - it is nothing but a page number;
 * - or it is a printer's slug ([PrintSlugs]): a layout file's name or a print time with its seconds, or a print time
 *   that another page of the window carries in its margin too, the same to the minute.
 *
 * A page needs at least one line outside both bands for any of this: otherwise there is no body to tell the
 * margins from.
 */
internal class MarginClutter(private val config: CleanupConfig) {
    /** [neighbours] are the other pages of the window; [target] is the page being cleaned. */
    fun find(target: PageProfile, neighbours: List<PageProfile>): Map<TextLine, MarginReason> {
        val window = neighbours + target
        val sides = sides(window)?.takeIf { hasBody(target, it) } ?: return emptyMap()
        val required = minOf(config.minRepeatPages, window.size).coerceAtLeast(MIN_PAGES)
        val dropped = LinkedHashMap<TextLine, MarginReason>()
        for (side in sides) {
            val others = neighbours.map { it.pageIndex to side.lines(it).filter { line -> side.inBand(line.box) } }
            for (line in side.lines(target).filter { side.inBand(it.box) }) {
                reason(line, target, others, required)?.let { dropped.putIfAbsent(line.line, it) }
            }
        }
        return dropped
    }

    private fun reason(
        line: MarginLine,
        target: PageProfile,
        others: List<Pair<Int, List<MarginLine>>>,
        required: Int,
    ): MarginReason? {
        val pageIndex = target.pageIndex
        val repeats = line.letters >= config.minSignatureLetters &&
            line.signature.hashCode() !in target.bodySignatures &&
            1 + others.count { (_, lines) -> lines.any { it.signature == line.signature } } >= required
        val runs = line.folios.any { value ->
            others.any { (page, lines) -> lines.any { other -> other.folios.any { it - page == value - pageIndex } } }
        }
        val printed = line.slug ||
            line.printTimes.any { time -> others.any { (_, lines) -> lines.any { time in it.printTimes } } }
        return when {
            repeats -> MarginReason.REPEATED
            runs -> MarginReason.RUNNING_FOLIO
            line.standaloneFolio -> MarginReason.STANDALONE_FOLIO
            printed -> MarginReason.PRINT_SLUG
            else -> null
        }
    }

    /** The top and bottom bands of the window's text area; null when no page has a line box. */
    private fun sides(window: List<PageProfile>): List<Side>? {
        val extent = window.mapNotNull { it.extent }.reduceOrNull(TextBox::union) ?: return null
        val band = extent.height * config.marginBandShare
        // A line belongs to a band when it reaches into it, so a tall header line still counts on a short page.
        return listOf(
            Side(PageProfile::top) { it.top >= extent.top - band },
            Side(PageProfile::bottom) { it.bottom <= extent.bottom + band },
        )
    }

    /**
     * A page whose every line sits in a band (a page of one or two lines, or a window of such pages) has no body to
     * tell its margins from: nothing on it is called a header.
     */
    private fun hasBody(target: PageProfile, sides: List<Side>): Boolean {
        val margin = target.top + target.bottom
        return target.bodySignatures.isNotEmpty() || margin.any { line -> sides.none { it.inBand(line.box) } }
    }

    private class Side(val lines: (PageProfile) -> List<MarginLine>, val inBand: (TextBox) -> Boolean)

    private companion object {
        /** A line on one page only can never be called repeated, however short the book. */
        const val MIN_PAGES = 2
    }
}
