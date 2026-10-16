// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/ListMarkers.kt
// Purpose: Recognizes where a list item starts: a bullet glyph, or a short number or letter label (P2-04).

package io.github.skm0078.audora.pdf.layout

/**
 * A bullet always starts an item. A label ("1.", "12)", "(a)", "(iv)", "a)") is also how a wrapped prose line can
 * begin ("… shown in Fig.\n3. We then …"), so [ParagraphBuilder] trusts one only after a line that ends a clause or
 * inside a list; numbers have at most two digits, so a year ("1947. The") never counts, and a bare letter needs a
 * bracket, since "a." is ordinary prose.
 */
internal object ListMarkers {
    /** Bullets, including the Symbol and Wingdings private-use codes PDFs map them to. */
    const val BULLETS = "•◦▪▫■□●○◆◇►▶‣⁃" +
        "∙❖➢✓"

    /** A line ending in one of these (closing quotes and brackets skipped) has finished its clause. */
    private const val CLAUSE_ENDS = ".!?…:;।॥"
    private const val CLOSERS = "\"'”’»)]"

    private val LABEL = Regex("""\(\p{Nd}{1,2}\)|\p{Nd}{1,2}[.)]|\((?:[a-z]|[ivx]{1,4})\)|[a-z]\)""")

    fun startsWithBullet(text: String, start: Int): Boolean = start < text.length && text[start] in BULLETS

    /** A label at [start], followed by whitespace (the label's own span may end there, so the line's end counts). */
    fun startsWithLabel(text: String, start: Int, lineEnd: Int): Boolean {
        val label = LABEL.matchAt(text, start) ?: return false
        val after = label.range.last + 1
        return after < lineEnd && text[after].isWhitespace()
    }

    /** Whether [text] is a label and nothing else: the sentence splitter cuts "1. Open the box" after "1.". */
    fun isLabel(text: String): Boolean = LABEL.matches(text.trim())

    /** Whether the text before [end] ends a clause: "… as follows:" or "… a sentence." */
    fun endsClause(text: String, end: Int): Boolean {
        var i = end - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] in CLOSERS)) i--
        return i >= 0 && text[i] in CLAUSE_ENDS
    }
}
