// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ReadingUnitBuilder.kt
// Purpose: Cuts a page's reading-order text into speakable ReadingUnits with stable charStart/charEnd.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayoutAnalyzer
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.ReadingParagraph
import io.github.skm0078.audora.pdf.layout.ReadingText
import io.github.skm0078.audora.pdf.layout.ReadingTextBuilder

/**
 * Layout (P2-02) puts the page in reading order; sentences are then split within each paragraph, so a heading
 * without a full stop never runs into the paragraph below it. `charStart`/`charEnd` index [ReadingText.text]
 * (ADR-018). Headers and de-hyphenation are P2-03; number and date normalization is P2-04. Units without a
 * letter or digit are skipped.
 */
class ReadingUnitBuilder(
    private val config: ReadingUnitConfig = ReadingUnitConfig(),
    private val splitter: SentenceSplitter = BreakIteratorSentenceSplitter(),
    layoutConfig: LayoutConfig = LayoutConfig(),
) {
    private val lineBoxes = LineBoxes(config.lineOverlapRatio)
    private val analyzer = PageLayoutAnalyzer(layoutConfig)
    private val textBuilder = ReadingTextBuilder()

    fun build(page: PageText): List<ReadingUnit> = build(readingText(page))

    /** The page's text in reading order: what [build]'s offsets index. */
    fun readingText(page: PageText): ReadingText = textBuilder.build(page, analyzer.analyze(page))

    fun build(reading: ReadingText): List<ReadingUnit> {
        val text = reading.text
        if (text.isBlank()) return emptyList()
        val pageLang = ScriptDetector.dominantLang(text) ?: Lang.EN
        // Same length as text, so the splitter's offsets are offsets into text.
        val flat = String(CharArray(text.length) { i -> if (text[i] in LINE_BREAKS) ' ' else text[i] })
        val units = ArrayList<ReadingUnit>()
        for (paragraph in reading.paragraphs) {
            val sentences = sentencesOf(flat, paragraph, pageLang)
            val pieces = sentences.flatMap { capLength(text, trim(text, it.first, it.last + 1)) }
            pieces.forEachIndexed { i, piece ->
                val raw = text.substring(piece.first, piece.last + 1)
                if (raw.none { it.isLetterOrDigit() }) return@forEachIndexed
                units.add(
                    ReadingUnit(
                        page = reading.pageIndex,
                        index = units.size,
                        charStart = piece.first,
                        charEnd = piece.last + 1,
                        text = raw.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" "),
                        lang = ScriptDetector.dominantLang(raw) ?: pageLang,
                        pauseAfterMs = pauseAfter(paragraph.kind, last = i == pieces.lastIndex),
                        bounds = lineBoxes.of(reading, piece.first, piece.last + 1),
                    ),
                )
            }
        }
        return units
    }

    /** Sentence ranges of [paragraph], as offsets into the whole text. */
    private fun sentencesOf(flat: String, paragraph: ReadingParagraph, pageLang: Lang): List<IntRange> {
        val slice = flat.substring(paragraph.start, paragraph.end)
        val lang = ScriptDetector.dominantLang(slice) ?: pageLang
        return splitter.split(slice, lang).map { (it.first + paragraph.start)..(it.last + paragraph.start) }
    }

    private fun pauseAfter(kind: ParagraphKind, last: Boolean): Int = when {
        kind == ParagraphKind.HEADING -> maxOf(config.sentencePauseMs, config.headingPauseMs)
        last -> maxOf(config.sentencePauseMs, config.paragraphPauseMs)
        else -> config.sentencePauseMs
    }

    /** [start] until [end] without leading and trailing whitespace; empty when it is all whitespace. */
    private fun trim(text: String, start: Int, end: Int): IntRange {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return s until e
    }

    /** Splits [range] into pieces of at most maxUnitChars, at the last whitespace before the limit. */
    private fun capLength(text: String, range: IntRange): List<IntRange> {
        if (range.isEmpty()) return emptyList()
        val pieces = ArrayList<IntRange>()
        var start = range.first
        val end = range.last + 1
        while (end - start > config.maxUnitChars) {
            val limit = start + config.maxUnitChars
            var cut = limit
            while (cut > start && !text[cut].isWhitespace()) cut--
            if (cut == start) cut = limit
            if (Character.isLowSurrogate(text[cut]) && cut > start) cut--
            pieces.add(trim(text, start, cut))
            start = trim(text, cut, end).first
        }
        pieces.add(start until end)
        return pieces.filterNot { it.isEmpty() }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val LINE_BREAKS = "\n\r\t"
    }
}
