// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TextRepair.kt
// Purpose: Repairs a paragraph's text for speech: hyphens at line ends, ligatures, junk characters, bullets, citations.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.pdf.extract.IntArrayBuilder
import io.github.skm0078.audora.pdf.layout.ListMarkers

/**
 * Text with the extracted-text offset of every UTF-16 unit, as ReadingText keeps it (ADR-018). Repairs only
 * delete units or expand one unit into several with the same source, so offsets stay valid and ascending.
 */
internal class MappedText(val text: String, val sources: IntArray) {
    init {
        require(sources.size == text.length) { "one source per UTF-16 unit" }
    }

    class Builder(capacity: Int) {
        private val text = StringBuilder(capacity)
        private val sources = IntArrayBuilder(capacity)

        fun last(): Char? = text.lastOrNull()

        fun append(c: Char, source: Int) {
            text.append(c)
            sources.add(source)
        }

        fun build(): MappedText = MappedText(text.toString(), sources.toArray())
    }

    companion object {
        /** [text] mapped onto itself: unit i comes from offset i. */
        fun plain(text: String): MappedText = MappedText(text, IntArray(text.length) { it })
    }
}

/**
 * Spec §5 step 6, ADR-020, on one paragraph at a time (lines separated by "\n"):
 * 1. **NFC** ([UnicodeRepair.compose], P3-02, ADR-028), then **fold** characters a listener cannot hear and a voice
 *    may stumble on: zero-width space, word joiner, BOM, soft hyphens, control characters (glyphs a font maps to
 *    nothing arrive as C0 controls). ZWJ and ZWNJ stay: they change how Devanagari and Bengali clusters form.
 *    Latin presentation-form ligatures ("ﬁ") are expanded. A "|" typed for a danda after Hindi or Bengali text
 *    becomes "।" ([UnicodeRepair.dandasFromPipes]), and so does a Bengali "৷" drawn for one
 *    ([UnicodeRepair.dandasFromBars]).
 * 2. **De-hyphenate.** PDFium replaces a hyphen at a line end with U+0002 and joins the lines (P2-02). Between two
 *    letters it is resolved: the hyphen stays when the word is printed with a hyphen elsewhere nearby
 *    ([lexicon], "block-pulse"), when the second part starts with a capital ("Cape-Horner") or the first
 *    part has a capital after its first letter ("qMRI-maps"); otherwise the word is joined ("nega-tion").
 *    A real dash at a line end ("well‐" U+2010, "—") joins the next line without a space.
 * 3. **Bullets** at the start of a line go: list items are read without their glyphs.
 * 4. **Citation markers** ("[4–7]", "[17,18]") go when [CleanupConfig.skipCitationMarkers] is on, with the space
 *    before them. A bracket holding a 0 or following a symbol is kept: "x ∈ [0, 1]" is mathematics.
 */
internal class TextRepair(private val config: CleanupConfig) {
    fun repair(paragraph: MappedText, lexicon: Set<String>): MappedText {
        var text = UnicodeRepair.dandasFromBars(UnicodeRepair.dandasFromPipes(fold(UnicodeRepair.compose(paragraph))))
        text = dehyphenate(text, lexicon)
        text = dropBullets(text)
        if (config.skipCitationMarkers) text = dropCitations(text)
        return text
    }

    private fun fold(input: MappedText): MappedText {
        val out = MappedText.Builder(input.text.length)
        input.text.forEachIndexed { i, c ->
            val ligature = LIGATURES[c]
            when {
                ligature != null -> ligature.forEach { out.append(it, input.sources[i]) }
                c == HYPHEN_MARKER || c in KEPT_CONTROLS -> out.append(c, input.sources[i])
                c in FOLDED || Character.getType(c) == Character.CONTROL.toInt() -> Unit
                else -> out.append(c, input.sources[i])
            }
        }
        return out.build()
    }

    private fun dehyphenate(input: MappedText, lexicon: Set<String>): MappedText {
        val text = input.text
        val out = MappedText.Builder(text.length)
        text.forEachIndexed { i, c ->
            when {
                c == HYPHEN_MARKER -> if (isBetweenLetters(text, i) && keepsHyphen(text, i, lexicon)) {
                    out.append('-', input.sources[i])
                }

                c == '\n' && joinsAfterDash(text, i) -> Unit

                else -> out.append(c, input.sources[i])
            }
        }
        return out.build()
    }

    private fun isBetweenLetters(text: String, i: Int): Boolean =
        i > 0 && i + 1 < text.length && Character.isLetter(text[i - 1]) && Character.isLetter(text[i + 1])

    private fun keepsHyphen(text: String, i: Int, lexicon: Set<String>): Boolean {
        var start = i
        while (start > 0 && Character.isLetter(text[start - 1])) start--
        var end = i + 1
        while (end < text.length && Character.isLetter(text[end])) end++
        val left = text.substring(start, i)
        val right = text.substring(i + 1, end)
        return "${left.lowercase()}-${right.lowercase()}" in lexicon ||
            right.first().isUpperCase() ||
            left.drop(1).any { it.isUpperCase() }
    }

    /** A line ending in a dash glued to a word ("well‐", "me--") runs on into the next line's first word. */
    private fun joinsAfterDash(text: String, i: Int): Boolean =
        i >= 2 && i + 1 < text.length && text[i - 1] in DASHES && !text[i - 2].isWhitespace() &&
            Character.isLetterOrDigit(text[i + 1])

    private fun dropBullets(input: MappedText): MappedText {
        val text = input.text
        val out = MappedText.Builder(text.length)
        var i = 0
        while (i < text.length) {
            val lineStart = i == 0 || text[i - 1] == '\n'
            if (lineStart && text[i] in ListMarkers.BULLETS) {
                // The bullet and the spaces after it.
                i++
                while (i < text.length && text[i] == ' ') i++
            } else {
                out.append(text[i], input.sources[i])
                i++
            }
        }
        return out.build()
    }

    /** "oedema\n[21,22]. Recent" becomes "oedema. Recent": the marker goes with the space or line break before it. */
    private fun dropCitations(input: MappedText): MappedText {
        val text = input.text
        val removed = BooleanArray(text.length)
        for (match in CITATION.findAll(text)) {
            if (isCitation(text, match)) for (k in match.range) removed[k] = true
        }
        if (removed.none { it }) return input
        val out = MappedText.Builder(text.length)
        text.forEachIndexed { i, c -> if (!removed[i]) out.append(c, input.sources[i]) }
        return out.build()
    }

    /** After a word or closing punctuation, and no zero among the numbers ("[0, 1]" is an interval). */
    private fun isCitation(text: String, match: MatchResult): Boolean {
        val before = lastVisibleBefore(text, match.range.first)
        val followsWord = before == null || before.isLetterOrDigit() || before in CITATION_AFTER
        return followsWord && DIGIT_RUN.findAll(match.value).none { it.value.all { digit -> digit == '0' } }
    }

    private fun lastVisibleBefore(text: String, end: Int): Char? {
        var i = end - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        return if (i >= 0) text[i] else null
    }

    private companion object {
        /** PDFium's mark for a hyphen at a line end (P2-02); unmapped glyphs arrive as the same code. */
        const val HYPHEN_MARKER = '\u0002'

        /** Zero-width space, word joiner, BOM, soft hyphen, Mongolian vowel separator (zero width since 6.3). */
        const val FOLDED = "\u200B\u2060\uFEFF\u00AD\u180E"

        /** Control characters that are whitespace: kept, so the words on either side stay apart. */
        const val KEPT_CONTROLS = "\n\r\t"

        /** Hyphen-minus, hyphen, non-breaking hyphen, en dash, em dash. */
        const val DASHES = "-\u2010\u2011\u2013\u2014"

        /** A citation may follow closing punctuation as well as a word: "(Gliomas’) [3]." */
        const val CITATION_AFTER = ")]\u2019\u201D\"'.,;:"

        val LIGATURES = mapOf(
            '\uFB00' to "ff",
            '\uFB01' to "fi",
            '\uFB02' to "fl",
            '\uFB03' to "ffi",
            '\uFB04' to "ffl",
            '\uFB05' to "st",
            '\uFB06' to "st",
        )

        /** "[4]", "[4–7]", "[17,18]", "[1, 3-5]", with an optional space or line break before it. */
        val CITATION = Regex("\\s?\\[\\p{Nd}{1,3}(?:\\s?[,\u2013-]\\s?\\p{Nd}{1,3})*]")
        val DIGIT_RUN = Regex("\\p{Nd}+")
    }
}
