// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/NoteMarkers.kt
// Purpose: Finds the note markers of one line: raised small digits and symbols after a word ("both.¹") (RI-5a).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.pdf.extract.PageText

/** A note marker: [span] of the page text, the note number it names ([ref], null when unreadable). */
internal class NoteMarker(val span: TextSpan, val ref: Int?, val atLineStart: Boolean)

/**
 * A marker is a run of glyphs that are small (`NoteConfig.markerSizeRatio`) and raised (`markerRaiseEm` above the
 * line's baseline), holding only numbers ("3", "8,16,17") or note symbols ("∗", "†"). Unicode superscript digits do
 * not count by themselves: legacy and broken font maps reuse "²" and "³" for Bengali conjuncts.
 * What comes before it tells a marker from an exponent:
 * - a word of two or more letters, not a unit ("both¹", but not "x²", "χ²" or "600 deg²");
 * - closing punctuation (", . ; : ’ ” ।"), or a bracket closing words ("(see below)¹"), never "(x+1)²";
 * - nothing, at the start of a line, when a word follows: a footnote's or an affiliation's own number ("¹Even if…").
 * After a digit, an operator or a single letter it is maths ("10⁻²¹", "Gpc⁻³", "B₁" sits low anyway). So is any
 * raised run with a maths operator ([besideOperator]) right after it or right before the word it follows: "E = mc²",
 * "ax² + bx", "ds² = dt² − dx²". "+" alone is no note sign: it is a charge ("Na⁺").
 */
internal class NoteMarkers(private val config: NoteConfig) {
    /** "3", "12", "8,16,17", "4–7". */
    private val numbers = config.maxMarkerDigits.let { n -> Regex("\\p{Nd}{1,$n}(?:[,\u2013-]\\p{Nd}{1,$n})*") }

    fun of(page: PageText, styles: GlyphStyles, line: TextLine): List<NoteMarker> {
        val glyphs = line.spans.flatMap { (it.start until it.end).toList() }.filterNot { page.text[it].isWhitespace() }
        val baseline = baseline(page, styles, line, glyphs) ?: return emptyList()
        val raised = glyphs.map { isRaised(page, styles, line, it, baseline) }
        return runsOf(raised).mapNotNull { marker(page.text, glyphs, it.first, it.last) }
    }

    /** The median bottom of the line's full-size glyphs; null when it has none or the line has no size. */
    private fun baseline(page: PageText, styles: GlyphStyles, line: TextLine, glyphs: List<Int>): Float? {
        if (line.size <= 0f) return null
        val bottoms = glyphs.filter { styles.sizeAt(it) > line.size * config.markerSizeRatio }
            .mapNotNull { page.boxAt(it)?.bottom }
            .sorted()
        return bottoms.getOrNull(bottoms.size / 2)
    }

    /** Index ranges of the runs of true in [flags]. */
    private fun runsOf(flags: List<Boolean>): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var start = -1
        for (i in 0..flags.size) {
            val on = i < flags.size && flags[i]
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                runs.add(start until i)
                start = -1
            }
        }
        return runs
    }

    private fun isRaised(page: PageText, styles: GlyphStyles, line: TextLine, offset: Int, baseline: Float): Boolean {
        val size = styles.sizeAt(offset)
        val box = page.boxAt(offset)
        return box != null && size > 0f && size <= line.size * config.markerSizeRatio &&
            box.bottom - baseline >= line.size * config.markerRaiseEm
    }

    /** The glyphs [from]..[to] of [glyphs] as a marker, or null when they are an exponent, a label or words. */
    private fun marker(text: String, glyphs: List<Int>, from: Int, to: Int): NoteMarker? {
        val start = glyphs[from]
        val end = glyphs[to] + 1
        // Glyphs drawn out of text order (a formula's parts) are no marker; a marker's glyphs follow in the text.
        val value = if (end > start) text.substring(start, end).filterNot { it.isWhitespace() } else ""
        if (value.length != to - from + 1 || !isMarkerText(value)) return null
        val atLineStart = from == 0
        val isMarker = if (atLineStart) {
            glyphs.getOrNull(to + 1)?.let { isWordChar(text[it]) } == true
        } else {
            // The text before it, as the listener hears it: in a fraction ("2GM/c²") the glyph left of the marker
            // on the line may be another part of the formula.
            (start - 1 downTo 0).firstOrNull { !text[it].isWhitespace() }?.let { follows(text, it) } == true &&
                !besideOperator(text, start, end)
        }
        return if (isMarker) NoteMarker(TextSpan(start, end), refOf(value), atLineStart) else null
    }

    private fun isMarkerText(value: String): Boolean = SYMBOLS_ONLY.matches(value) || numbers.matches(value)

    /** Whether a marker may follow the glyph at [before]: a word, closing punctuation or a bracket closing words. */
    private fun follows(text: String, before: Int): Boolean {
        val c = text[before]
        return when {
            isWordChar(c) -> wordBefore(text, before + 1).let { word ->
                word.length >= MIN_WORD_LETTERS && word !in UNITS && word.none(::isGreek)
            }

            c in CLOSING_PUNCTUATION -> true

            c in CLOSING_BRACKETS -> wordBefore(text, before).length >= MIN_WORD_LETTERS

            else -> false
        }
    }

    /** A maths operator is the first thing after [end], or the last thing before the word that ends at [start]. */
    private fun besideOperator(text: String, start: Int, end: Int): Boolean {
        val after = (end until text.length).firstOrNull { !text[it].isWhitespace() }?.let { text[it] }
        val wordStart = start - wordBefore(text, start).length
        val before = (wordStart - 1 downTo 0).firstOrNull { !text[it].isWhitespace() }?.let { text[it] }
        return after.isOperator() || before.isOperator()
    }

    /** The letters (and the vowel signs between them) right before [end]. */
    private fun wordBefore(text: String, end: Int): String {
        var start = end
        while (start > 0 && isWordChar(text[start - 1])) start--
        return text.substring(start, end)
    }

    internal companion object {
        /** Combining vowel signs count: "का" is a two-letter word, though "ा" is not a letter to Java. */
        fun isWordChar(c: Char): Boolean {
            val type = Character.getType(c)
            return Character.isLetter(c) ||
                type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
        }

        /** Maths operators: next to one, a raised digit after a word is an exponent ("mc²"), not a note marker. */
        const val MATHS_OPERATORS = "=+−×÷/<>^±∓≤≥≈≠"

        fun Char?.isOperator(): Boolean = this != null && this in MATHS_OPERATORS

        /** Greek letters are maths variables here ("χ²", "ðχ²" from a broken font map), not words. */
        private fun isGreek(c: Char) = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.GREEK

        /** The note a marker names: its number, or a symbol's place in the usual order (∗ 1, † 2, ‡ 3, …). */
        fun refOf(value: String): Int? {
            val symbol = SYMBOL_ORDER.indexOf(value.first().let { if (it == ASTERISK_OPERATOR) '*' else it })
            if (symbol >= 0) return symbol + 1
            val digits = value.takeWhile { Character.isDigit(it) }
            return digits.fold(0) { n, d -> n * DECIMAL + Character.digit(d, DECIMAL) }.takeIf { digits.isNotEmpty() }
        }

        private const val DECIMAL = 10
        private const val MIN_WORD_LETTERS = 2

        /** Note symbols in the order they are used in when a page has several; "∗" (U+2217) counts as "*". */
        private const val SYMBOL_ORDER = "*†‡§¶‖#\$+"
        private const val ASTERISK_OPERATOR = '∗'
        private val SYMBOLS_ONLY = Regex("[*∗†‡§¶‖#\$]{1,3}")

        private const val CLOSING_PUNCTUATION = ",.;:!?\"'\u2019\u201D।॥"
        private const val CLOSING_BRACKETS = ")]"

        /** Units squared or cubed: "600 deg²", "12 cm³" are exponents. */
        private val UNITS = setOf(
            "mm", "cm", "km", "nm", "µm", "μm", "ft", "yd", "mi", "deg", "pc", "kpc", "Mpc", "Gpc", "sec", "yr",
            "kg", "Hz", "ha",
        )
    }
}
