// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ListMarkers.kt
// Purpose: Recognizes where a list item starts: a bullet glyph, or a short number or letter label (P2-04).

package io.github.skm0078.audora.pdf.layout

/**
 * A bullet always starts an item. A label ("1.", "12)", "(a)", "a)", "b.", "(iv)", "iv.", "(A)", "B)", "(IV)",
 * "(क)", "(अ)", "(ক)", "(অ)") is also how a wrapped prose line can begin ("… shown in Fig.\n3. We then …"), so
 * [ParagraphBuilder] trusts one only after a line that ends a clause or inside a list; numbers have at most two
 * digits, so a year ("1947. The") never counts. A capital letter or Roman numeral needs a bracket: "A. Smith" and
 * "I. Introduction" open a line of prose or a heading, not an item. A single small letter with a full stop starts an
 * item only in sequence ([inSequence]): a wrapped line may open with "p. 12", "v. 3" or "c. 1900".
 */
internal object ListMarkers {
    /** Bullets, including the Symbol and Wingdings private-use codes PDFs map them to. */
    const val BULLETS = "•◦▪▫■□●○◆◇►▶‣⁃" +
        "∙❖➢✓"

    /** A line ending in one of these (closing quotes and brackets skipped) has finished its clause. */
    private const val CLAUSE_ENDS = ".!?…:;।॥"
    private const val CLOSERS = "\"'”’»)]"

    private val LABEL = Regex(
        listOf(
            """\(\p{Nd}{1,2}\)""", // (3)
            """\p{Nd}{1,2}[.)]""", // 1. 12)
            """\((?:[a-z]|[ivx]{1,4}|[A-Z]|[IVX]{1,4})\)""", // (a) (iv) (A) (IV)
            """\([\u0905-\u0939\u0985-\u09B9]\)""", // (अ) (क) (অ) (ক): vowels and consonants
            """(?:[a-z]|[ivx]{1,4})[.)]""", // a) b. iv. ii)
            """[A-Z]\)""", // B)
        ).joinToString("|"),
    )

    fun startsWithBullet(text: String, start: Int): Boolean = start < text.length && text[start] in BULLETS

    /** A label at [start], followed by whitespace (the label's own span may end there, so the line's end counts). */
    fun startsWithLabel(text: String, start: Int, lineEnd: Int): Boolean = labelAt(text, start, lineEnd) != null

    /** The label at [start] followed by whitespace before [lineEnd] ("b."), or null. */
    fun labelAt(text: String, start: Int, lineEnd: Int): String? {
        val label = LABEL.matchAt(text, start) ?: return null
        val after = label.range.last + 1
        return label.value.takeIf { after < lineEnd && text[after].isWhitespace() }
    }

    /**
     * [label] may open the next item after the items labelled [previous] in the same block, in order: any label but
     * a single small letter with a full stop, which must open a list ("a.", "i.") or follow the letter or Roman
     * numeral before it somewhere in the list ("c." after "b)", "v." after "iv.", "b." after "a. / (i) / (ii)" in a
     * list nested flush).
     */
    fun inSequence(label: String, previous: List<String>): Boolean {
        if (!LETTER_STOP.matches(label)) return true
        val letter = label.first()
        val roman = romanValue(letter.toString())
        return letter == 'a' || letter == 'i' || previous.map { it.trim('(', ')', '.') }.any { before ->
            before == (letter - 1).toString() || (roman != null && romanValue(before) == roman - 1)
        }
    }

    /** The label opening [line] of [text] ([labelAt]), or null. */
    fun labelOf(text: String, line: TextLine): String? = labelAt(text, line.spans.first().start, line.spans.last().end)

    /**
     * [line] opens a list item after the items labelled [previous] in its block: a bullet, or a label in sequence
     * ([inSequence]). The caller adds where a label counts: inside a list or after a line that ends a clause.
     */
    fun opensItem(text: String, line: TextLine, previous: List<String>): Boolean =
        startsWithBullet(text, line.spans.first().start) ||
            labelOf(text, line)?.let { inSequence(it, previous) } == true

    /** A small Roman numeral's value ("iv": 4), or null. */
    private fun romanValue(text: String): Int? {
        val values = text.map { ROMAN[it] ?: return null }
        return values.indices.sumOf { i ->
            if (values.getOrNull(i + 1)?.let { it > values[i] } ==
                true
            ) {
                -values[i]
            } else {
                values[i]
            }
        }
    }

    private val LETTER_STOP = Regex("[a-z]\\.")
    private val ROMAN = mapOf('i' to 1, 'v' to ROMAN_FIVE, 'x' to ROMAN_TEN)
    private const val ROMAN_FIVE = 5
    private const val ROMAN_TEN = 10

    /** Whether [text] is a label and nothing else: the sentence splitter cuts "1. Open the box" after "1.". */
    fun isLabel(text: String): Boolean = LABEL.matches(text.trim())

    /** Whether the text before [end] ends a clause: "… as follows:" or "… a sentence." */
    fun endsClause(text: String, end: Int): Boolean {
        var i = end - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] in CLOSERS)) i--
        return i >= 0 && text[i] in CLAUSE_ENDS
    }
}
