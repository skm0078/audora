// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/QuoteCarry.kt
// Purpose: Which sentences start inside a quote opened earlier in their paragraph, and who speaks them (items 7, 63).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DialogueConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.route.QuoteSplitter
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.route.SpeakerTags
import java.util.concurrent.ConcurrentHashMap

/**
 * Each page is read whole when it is extracted ([page]), before any of its sentences render, so the renderer can ask
 * about any one sentence ([startsInQuote]) without the sentences around it. A quote is carried only within a
 * paragraph: it ends at a paragraph's end (a pause after the sentence longer than a sentence's, from `:pdf` layout,
 * the same boundary skim uses), at a change of kind, at a heading or table, and at the top of a page, so a stray OCR
 * mark never turns a whole chapter into dialogue. Who speaks each quoted sentence ([speakerOf]) comes from
 * [SpeakerTurns]; the conversation carries over from the page before when that page is still remembered.
 *
 * Thread-safe: the session writes on the main thread, the renderer reads on its worker. Only the last
 * [DialogueConfig.rememberedPages] pages are kept.
 */
class QuoteCarry(
    private val splitter: QuoteSplitter = QuoteSplitter(),
    private val config: DialogueConfig = DialogueConfig(),
    private val unitConfig: ReadingUnitConfig = ReadingUnitConfig(),
) {
    private val pages = ConcurrentHashMap<Int, PageQuotes>()
    private val order = ArrayDeque<Int>()
    private val turns = QuoteSplitter(config.copy(minQuoteWords = 1)).let { anyQuote ->
        // Every quote is a turn of the conversation, a one-word "Yes" too, though only longer ones change voice.
        SpeakerTurns(SpeakerTags(anyQuote), anyQuote, config.rememberedSpeakers)
    }

    private class PageQuotes(val opened: Set<Int>, val speakers: Map<Int, Speaker>, val end: Turns)

    /** One extracted page's units, in reading order. */
    fun page(units: List<ReadingUnit>) {
        val page = units.firstOrNull()?.page ?: return
        val paragraphs = paragraphs(units)
        val spoken = turns.page(paragraphs, pages[page - 1]?.end ?: Turns())
        val opened = paragraphs.flatten().filter { it.carried }.mapTo(HashSet()) { UnitKey.of(it.unit).index }
        pages[page] = PageQuotes(opened, spoken.speakers, spoken.end)
        synchronized(order) {
            order.remove(page)
            order.addLast(page)
            while (order.size > config.rememberedPages) pages.remove(order.removeFirst())
        }
    }

    fun startsInQuote(unit: ReadingUnit): Boolean = pages[unit.page]?.opened?.contains(unit.index) == true

    /** Who says this sentence's quotes, when known (plan item 63). */
    fun speakerOf(unit: ReadingUnit): Speaker? = pages[unit.page]?.speakers?.get(unit.index)

    /** A new book opened. */
    fun clear() {
        synchronized(order) {
            order.clear()
            pages.clear()
        }
    }

    /** The page's paragraphs, each sentence marked with whether it starts inside an earlier sentence's quote. */
    private fun paragraphs(units: List<ReadingUnit>): List<List<Sentence>> {
        val paragraphs = mutableListOf<MutableList<Sentence>>()
        var open = false
        units.forEachIndexed { i, unit ->
            val previous = units.getOrNull(i - 1)
            val same = previous != null && sameParagraph(previous, unit)
            val carried = open && same
            if (!same) paragraphs.add(mutableListOf())
            paragraphs.last().add(Sentence(unit, carried))
            open = unit.kind in PROSE && splitter.endsInQuote(unit.text, carried)
        }
        return paragraphs
    }

    private fun sameParagraph(previous: ReadingUnit, unit: ReadingUnit): Boolean =
        previous.kind == unit.kind && previous.pauseAfterMs <= unitConfig.sentencePauseMs
}

/** The kinds a quote can open in and a character can speak; a heading or a table row is never dialogue. */
internal val PROSE = setOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.ASIDE)
