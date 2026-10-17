// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/QuoteCarry.kt
// Purpose: Which sentences start inside a quote opened by an earlier sentence of their paragraph (plan item 7).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.route.QuoteSplitter
import java.util.concurrent.ConcurrentHashMap

/**
 * Each page is read whole when it is extracted ([page]), before any of its sentences render, so the renderer can ask
 * about any one sentence ([startsInQuote]) without the sentences around it. A quote is carried only within a
 * paragraph: it ends at a paragraph's end (a pause after the sentence longer than a sentence's, from `:pdf` layout,
 * the same boundary skim uses), at a change of kind, at a heading or table, and at the top of a page, so a stray OCR
 * mark never turns a whole chapter into dialogue.
 *
 * Thread-safe: the session writes on the main thread, the renderer reads on its worker. Only the last
 * [DialogueConfig.rememberedPages] pages are kept.
 */
class QuoteCarry(
    private val splitter: QuoteSplitter = QuoteSplitter(),
    private val config: DialogueConfig = DialogueConfig(),
    private val unitConfig: ReadingUnitConfig = ReadingUnitConfig(),
) {
    private val pages = ConcurrentHashMap<Int, Set<Int>>()
    private val order = ArrayDeque<Int>()

    /** One extracted page's units, in reading order. */
    fun page(units: List<ReadingUnit>) {
        val page = units.firstOrNull()?.page ?: return
        pages[page] = openedIndexes(units)
        synchronized(order) {
            order.remove(page)
            order.addLast(page)
            while (order.size > config.rememberedPages) pages.remove(order.removeFirst())
        }
    }

    fun startsInQuote(unit: ReadingUnit): Boolean = pages[unit.page]?.contains(unit.index) == true

    /** A new book opened. */
    fun clear() {
        synchronized(order) {
            order.clear()
            pages.clear()
        }
    }

    private fun openedIndexes(units: List<ReadingUnit>): Set<Int> {
        val opened = HashSet<Int>()
        var open = false
        units.forEachIndexed { i, unit ->
            val previous = units.getOrNull(i - 1)
            val carried = open && previous != null && sameParagraph(previous, unit)
            if (carried) opened.add(UnitKey.of(unit).index)
            open = unit.kind in PROSE && splitter.endsInQuote(unit.text, carried)
        }
        return opened
    }

    private fun sameParagraph(previous: ReadingUnit, unit: ReadingUnit): Boolean =
        previous.kind == unit.kind && previous.pauseAfterMs <= unitConfig.sentencePauseMs

    private companion object {
        val PROSE = setOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.ASIDE)
    }
}
