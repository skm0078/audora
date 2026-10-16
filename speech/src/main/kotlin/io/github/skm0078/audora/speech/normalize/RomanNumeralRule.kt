// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/RomanNumeralRule.kt
// Purpose: Reads Roman numerals where they cannot be words: after "Chapter"/"Part", regnal names, (iv), headings.

package io.github.skm0078.audora.speech.normalize

/**
 * Roman numerals are ambiguous with words ("I", "MIX") and letter labels ("Part C", "Section D"), so they are read
 * only in four safe places:
 * 1. after a structural keyword: "Chapter IV" → "Chapter four", "World War II" → "World War two";
 * 2. after a monarch's or pope's name: "Henry VIII" → "Henry the eighth";
 * 3. as a parenthesised list label made of i, v and x only: "(iv)" → "(four)";
 * 4. at the start of a heading, followed by ".", ")" or ":" or standing alone: "IV. Results" → "four. Results".
 *
 * A lone L, C, D or M is never a numeral (it is a letter label); only canonical numerals are read (no "IIII").
 */
internal class RomanNumeralRule : NormalizationRule {

    override fun apply(text: String, role: TextRole): String {
        var result = AFTER_KEYWORD.replace(text) { match ->
            val (keyword, numeral) = match.destructured
            cardinalOf(numeral)?.let { "$keyword $it" } ?: match.value
        }
        result = REGNAL.replace(result) { match ->
            val (name, numeral) = match.destructured
            valueOf(numeral)?.let { "$name the ${NumberWords.ordinal(it.toLong())}" } ?: match.value
        }
        result = LIST_LABEL.replace(result) { match ->
            cardinalOf(match.groupValues[1])?.let { "($it)" } ?: match.value
        }
        if (role == TextRole.HEADING) {
            result = HEADING_START.replace(result) { match ->
                val (lead, numeral) = match.destructured
                cardinalOf(numeral)?.let { lead + it } ?: match.value
            }
        }
        return result
    }

    private fun cardinalOf(numeral: String): String? = valueOf(numeral)?.let { NumberWords.cardinal(it.toLong()) }

    private companion object {
        private val CANONICAL = Regex("""M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})""")
        private val VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        private val SINGLE_LETTER_LABELS = setOf("L", "C", "D", "M")

        /** Value of a canonical numeral in one case, or null for letter labels and malformed numerals. */
        fun valueOf(numeral: String): Int? {
            val upper = numeral.uppercase()
            val isOneCase = numeral == upper || numeral == numeral.lowercase()
            val isLetterLabel = upper.isEmpty() || upper in SINGLE_LETTER_LABELS
            if (!isOneCase || isLetterLabel || !CANONICAL.matches(upper)) return null
            val values = upper.map(VALUES::getValue)
            return values.indices.sumOf { i ->
                if (i + 1 < values.size && values[i] < values[i + 1]) -values[i] else values[i]
            }
        }

        private const val KEYWORDS =
            """chapter|part|book|section|volume|appendix|act|scene|article|schedule|annexure|annex|unit|lesson|""" +
                """canto|phase|class|grade|plate|table|figure|type|stage|level|round|title|division|paper|psalm|""" +
                """world war"""
        private const val NAMES =
            """Henry|Edward|George|William|Charles|James|Richard|Louis|Elizabeth|Mary|Victoria|Pius|John|Paul|""" +
                """Benedict|Leo|Gregory|Innocent|Clement|Peter|Catherine|Frederick|Philip|Napoleon|Nicholas|""" +
                """Alexander|Ivan|Ramesses|Ptolemy|Constantine|Wilhelm|Ferdinand|Alfonso|Juan|Carlos"""

        // Upper or lower case numeral (never mixed), ending the word and not followed by an apostrophe word.
        private const val NUMERAL = """([IVXLCDM]+|[ivxlcdm]+)(?![\w'’])"""

        val AFTER_KEYWORD = Regex("""(?<![\w])((?i:$KEYWORDS))\s+$NUMERAL""")

        // A lone "I" after a name is read only before punctuation or the end ("Henry I." but not "when John I met").
        val REGNAL = Regex("""(?<![\w])($NAMES)\s+([IVXL]{2,}|[VX]|I(?=\s*[.,;:)]|\s*$))(?![\w'’])""")
        val LIST_LABEL = Regex("""\(([ivx]+|[IVX]+)\)""")
        val HEADING_START = Regex("""^(\s*)([IVXLCDM]+|[ivxlcdm]+)(?=[.):](?:\s|$)|\s*$)""")
    }
}
