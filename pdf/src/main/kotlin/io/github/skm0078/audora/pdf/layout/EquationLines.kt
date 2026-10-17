// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/EquationLines.kt
// Purpose: Finds the display equations of a text block: lines of maths set off from the text (RI-7a, ADR-035).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.FurnitureConfig
import io.github.skm0078.audora.pdf.quality.WordModels
import io.github.skm0078.audora.pdf.quality.Words

/**
 * A display equation is a line that reads as maths ([isEquation]: few dictionary words, and a maths symbol or an
 * equation number) and is set off from the text: by an equation number, an indent from the block's left edge
 * (centred or indented), or a block that holds nothing else. The tail of a wrapped sentence ("for i = 1, 2, …,
 * m − 1.") starts at the left edge and stays in its sentence.
 */
internal class EquationLines(private val config: FurnitureConfig, private val models: WordModels) {
    /** The display lines among [lines], the lines of one block; [texts] holds each line's text. */
    fun of(lines: List<TextLine>, texts: Map<TextLine, String>, bodySize: Float): Set<TextLine> {
        val candidates = lines.filter { isEquation(texts.getValue(it)) }
        if (candidates.isEmpty() || candidates.size == lines.size) return candidates.toSet()
        val left = lines.mapNotNull { it.box?.left }.minOrNull()
        val minIndent = config.equationMinIndentEm * bodySize
        return candidates.filter { line ->
            val box = line.box
            val indented = left != null && box != null && bodySize > 0f && box.left - left >= minIndent
            indented || EQUATION_NUMBER_AT_END.containsMatchIn(texts.getValue(line))
        }.toSet()
    }

    /**
     * Not maths: code and garbled glyph runs ([NOT_MATHS]: "$ git", "?>9@AJ"), "<placeholder>" in command lines, and
     * Greek-looking letters on an Indic line, which are a broken font's Bengali or Hindi, not variables.
     */
    fun isEquation(line: String): Boolean {
        val text = PLACEHOLDER.replace(line) { it.groupValues[1] }
        val indic = text.codePoints().anyMatch { Character.UnicodeScript.of(it) in INDIC }
        var visible = 0
        var maths = 0
        text.codePoints().forEach { cp ->
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) visible++
            if (isMaths(cp, greek = !indic)) maths++
        }
        val numbered = EQUATION_NUMBER_AT_END.containsMatchIn(text)
        val looksLikeMaths = visible > 0 && (maths > 0 || numbered) && text.none { it in NOT_MATHS }
        val maxShare = if (numbered && maths > 0) config.numberedEquationMaxWordShare else config.equationMaxWordShare
        return looksLikeMaths && knownWordLetters(text) <= visible * maxShare
    }

    /** Letters in dictionary words of at least `minEquationWordLetters`: "dy" and "or" are variables as often. */
    private fun knownWordLetters(text: String): Int = Words.of(text).sumOf { word ->
        val letters = word.codePointCount(0, word.length)
        val known = letters >= config.minEquationWordLetters &&
            Words.langOf(word)?.let(models::of)?.contains(word) == true
        if (known) letters else 0
    }

    private fun isMaths(cp: Int, greek: Boolean): Boolean {
        val greekLetter = greek && Character.UnicodeScript.of(cp) == Character.UnicodeScript.GREEK
        return greekLetter || Character.getType(cp) == Character.MATH_SYMBOL.toInt() || cp in MATHS_EXTRA
    }

    private companion object {
        /** "(12)", "(3.4)", "(7a)"; at most three digits a part, so a year ("(2016)") is not one. */
        val EQUATION_NUMBER_AT_END = Regex("\\(\\p{Nd}{1,3}(?:\\.\\p{Nd}{1,3})*[a-z]?\\)\\s*$")

        /** Characters maths never uses but code and garbled glyph runs do. */
        const val NOT_MATHS = "#$@?&\\\""
        val PLACEHOLDER = Regex("<([\\p{L}\\p{N}._/-]+)>")
        val INDIC = setOf(Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI)

        /** Maths that Unicode files under other categories: partial, nabla, infinity, primes. */
        val MATHS_EXTRA = setOf(0x2202, 0x2207, 0x221E, 0x2032, 0x2033)
    }
}
