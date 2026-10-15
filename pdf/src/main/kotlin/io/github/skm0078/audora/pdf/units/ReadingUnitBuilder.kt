// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ReadingUnitBuilder.kt
// Purpose: Cuts a page's text into speakable ReadingUnits with stable charStart/charEnd (P1-02, no layout yet).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.extract.PageText

/**
 * Sentence split + whitespace collapse only. Lines, columns, headers and de-hyphenation are Phase 2
 * (P2-02, P2-03); number and date normalization is P2-04. Units without a letter or digit are skipped.
 */
class ReadingUnitBuilder(
    private val config: ReadingUnitConfig = ReadingUnitConfig(),
    private val splitter: SentenceSplitter = BreakIteratorSentenceSplitter(),
) {
    private val lineBoxes = LineBoxes(config.lineOverlapRatio)

    fun build(page: PageText): List<ReadingUnit> {
        val text = page.text
        if (text.isBlank()) return emptyList()
        val pageLang = ScriptDetector.dominantLang(text) ?: Lang.EN
        // Same length as text, so the splitter's offsets are offsets into text.
        val flat = String(CharArray(text.length) { i -> if (text[i] in LINE_BREAKS) ' ' else text[i] })
        val units = ArrayList<ReadingUnit>()
        for (sentence in splitter.split(flat, pageLang)) {
            for (piece in capLength(text, trim(text, sentence.first, sentence.last + 1))) {
                val raw = text.substring(piece.first, piece.last + 1)
                if (raw.none { it.isLetterOrDigit() }) continue
                units.add(
                    ReadingUnit(
                        page = page.pageIndex,
                        index = units.size,
                        charStart = piece.first,
                        charEnd = piece.last + 1,
                        text = raw.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" "),
                        lang = ScriptDetector.dominantLang(raw) ?: pageLang,
                        pauseAfterMs = config.sentencePauseMs,
                        bounds = lineBoxes.of(page, piece.first, piece.last + 1),
                    ),
                )
            }
        }
        return units
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
