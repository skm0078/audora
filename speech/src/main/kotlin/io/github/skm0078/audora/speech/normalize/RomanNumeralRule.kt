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
 * A lone L, C, D or M is never a numeral (it is a letter label); only canonical numerals are read (no "IIII"). A
 * lone "I" after a keyword is the pronoun when a verb follows it ("the book I read", "the part I played", "the class
 * I teach"), and stays as written; before anything else it is the numeral ("Part I.", "Type I diabetes", "Book I of
 * the Iliad"). After "World War" it is always the numeral ("World War I began").
 */
internal class RomanNumeralRule : NormalizationRule {

    override fun apply(text: String, role: TextRole): String {
        var result = AFTER_KEYWORD.replace(text) { match ->
            val (keyword, numeral) = match.destructured
            val pronoun = numeral.equals("I", ignoreCase = true) && isPronoun(keyword, text, match.range.last + 1)
            if (pronoun) match.value else cardinalOf(numeral)?.let { "$keyword $it" } ?: match.value
        }
        result = REGNAL.replace(result) { match ->
            val (name, numeral) = match.destructured
            RomanNumerals.valueOf(numeral)?.let { "$name the ${NumberWords.ordinal(it.toLong())}" } ?: match.value
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

    /** A lone "I" at [after] is the pronoun when [PRONOUN_VERB] follows, except after "World War" (two words). */
    private fun isPronoun(keyword: String, text: String, after: Int): Boolean =
        ' ' !in keyword && PRONOUN_VERB.matchesAt(text, after)

    private fun cardinalOf(numeral: String): String? = RomanNumerals.valueOf(numeral)?.let {
        NumberWords.cardinal(it.toLong())
    }

    private companion object {
        private const val KEYWORDS =
            """chapter|part|book|section|volume|appendix|act|scene|article|schedule|annexure|annex|unit|lesson|""" +
                """canto|phase|class|grade|plate|table|figure|type|stage|level|round|title|division|paper|psalm|""" +
                """world war"""

        // Upper or lower case numeral (never mixed), ending the word and not followed by an apostrophe word.
        private const val NUMERAL = """([IVXLCDM]+|[ivxlcdm]+)(?![\w'’])"""

        val AFTER_KEYWORD = Regex("""(?<![\w])((?i:$KEYWORDS))\s+$NUMERAL""")

        // What follows the pronoun "I" and never the numeral: a verb (an auxiliary, a common verb, any "-ed" word) or
        // an adverb that sits between them ("the book I also read").
        private const val VERBS =
            """am|was|were|have|had|has|do|did|does|will|would|shall|should|can|could|may|might|must|need|want|""" +
                """like|love|hate|know|knew|think|thought|mean|meant|read|write|wrote|make|made|get|got|give|gave|""" +
                """find|found|take|took|see|saw|feel|felt|left|meet|met|teach|taught|buy|bought|bring|brought|""" +
                """hear|heard|keep|kept|lose|lost|tell|told|say|said|go|went|come|came|began|run|ran|choose|chose|""" +
                """spent|built|sent|held|won|prefer|use|study|attend|remember|forget|forgot|""" +
                """also|never|just|still|really|once|ever|always|often|only|already|\p{Ll}+ed"""
        val PRONOUN_VERB = Regex("""\s+(?:$VERBS)(?![\w'’])""")

        private val NAMES = RomanNumerals.REGNAL_NAMES.joinToString("|")

        // A lone "I" after a name is read only before punctuation or the end ("Henry I." but not "when John I met").
        val REGNAL = Regex("""(?<![\w])($NAMES)\s+([IVXL]{2,}|[VX]|I(?=\s*[.,;:)]|\s*$))(?![\w'’])""")
        val LIST_LABEL = Regex("""\(([ivx]+|[IVX]+)\)""")
        val HEADING_START = Regex("""^(\s*)([IVXLCDM]+|[ivxlcdm]+)(?=[.):](?:\s|$)|\s*$)""")
    }
}

/** Regnal names and Roman numeral values, shared with [InitialsRule] and the Hindi and Bengali rules. */
internal object RomanNumerals {
    /** Monarchs' and popes' names a numeral follows ("Henry VIII"), shared with [InitialsRule]. */
    val REGNAL_NAMES: Set<String> = setOf(
        "Henry", "Edward", "George", "William", "Charles", "James", "Richard", "Louis", "Elizabeth", "Mary",
        "Victoria", "Pius", "John", "Paul", "Benedict", "Leo", "Gregory", "Innocent", "Clement", "Peter", "Catherine",
        "Frederick", "Philip", "Napoleon", "Nicholas", "Alexander", "Ivan", "Ramesses", "Ptolemy", "Constantine",
        "Wilhelm", "Ferdinand", "Alfonso", "Juan", "Carlos",
    )

    private val CANONICAL = Regex("""M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})""")

    @Suppress("MagicNumber") // The numerals' own values.
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
}
