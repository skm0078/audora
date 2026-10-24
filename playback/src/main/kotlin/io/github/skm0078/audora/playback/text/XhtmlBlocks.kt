// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/XhtmlBlocks.kt
// Purpose: Turns one EPUB content document (XHTML) into headings, paragraphs and list items, notes last.

package io.github.skm0078.audora.playback.text

/**
 * Block elements end a block; inline ones (em, span, a) only add text. Skipped: the head, scripts and styles, a
 * navigation list, ruby annotations and note-reference markers (the superscript "12" a reader would hear). Footnotes
 * and endnotes (`epub:type` or `role`) are moved to the end of the document, as the PDF pipeline does with notes.
 * Table cells are joined with "; " so a row is read as one sentence-like block.
 */
object XhtmlBlocks {
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val BLOCKS = HEADINGS + setOf(
        "p", "div", "li", "blockquote", "pre", "tr", "dt", "dd", "figcaption", "caption", "section", "article",
        "aside", "header", "footer", "ul", "ol", "dl", "table", "body", "hr", "address", "center",
    )
    private val SKIPPED = setOf(
        "head", "script", "style", "nav", "rt", "rp", "svg", "math", "title",
        // In saved web pages (ADR-066): page furniture and form controls, not reading text.
        "noscript", "template", "iframe", "button", "select",
    )
    private val NOTE_TYPES = setOf("footnote", "endnote", "rearnote", "note")
    private val NOTE_ROLES = setOf("doc-footnote", "doc-endnote", "doc-endnotes", "doc-footnotes")
    private val CELLS = setOf("td", "th")
    private const val CELL_SEPARATOR = "; "
    private val WHITESPACE = Regex("\\s+")

    fun blocks(markup: List<Markup>): List<TextBlock> {
        val state = Walk()
        for (token in markup) {
            when (token) {
                is Markup.Start -> state.start(token)
                is Markup.End -> state.end(token.localName)
                is Markup.Text -> state.text(token.text)
            }
        }
        state.flush()
        return state.body + state.notes
    }

    /** The first heading's text, for a chapter the contents file does not name. */
    fun firstHeading(blocks: List<TextBlock>): String? = blocks.firstOrNull { it.kind == BlockKind.HEADING }?.text

    private class Walk {
        val body = ArrayList<TextBlock>()
        val notes = ArrayList<TextBlock>()
        private val buffer = StringBuilder()

        /** A cell ended: the separator goes in only if more text follows in the same row. */
        private var cellEnded = false

        /** Open elements: name, and whether it is a skipped subtree or a note. */
        private val open = ArrayList<Pair<String, Kind>>()

        fun start(tag: Markup.Start) {
            val name = tag.localName
            if (name == "br") {
                buffer.append(' ')
                return
            }
            if (name in BLOCKS) flush()
            if (tag.selfClosing) return
            open += name to kindOf(name, tag.attributes)
        }

        fun end(name: String) {
            val index = open.indexOfLast { it.first == name }
            if (index < 0) return
            if (name in CELLS && !skipping()) cellEnded = true
            if (name in BLOCKS) flush()
            // Closing an element closes any left open inside it (unclosed <p> and <li> are common).
            while (open.size > index) open.removeAt(open.lastIndex)
        }

        fun text(text: String) {
            if (skipping()) return
            if (cellEnded && text.isNotBlank()) {
                buffer.append(CELL_SEPARATOR)
                cellEnded = false
            }
            buffer.append(text)
        }

        fun flush() {
            val text = buffer.toString().replace(WHITESPACE, " ").trim()
            buffer.clear()
            cellEnded = false
            if (text.none { it.isLetterOrDigit() } || skipping()) return
            val names = open.map { it.first }
            val kind = when {
                names.any { it in HEADINGS } -> BlockKind.HEADING
                "li" in names -> BlockKind.LIST_ITEM
                else -> BlockKind.BODY
            }
            (if (open.any { it.second == Kind.NOTE }) notes else body) += TextBlock(kind, text)
        }

        private fun skipping() = open.any { it.second == Kind.SKIP }

        private fun kindOf(name: String, attributes: Map<String, String>): Kind {
            val types = (attributes["epub:type"].orEmpty() + " " + attributes["role"].orEmpty()).split(' ').toSet()
            return when {
                name in SKIPPED -> Kind.SKIP
                "noteref" in types || "doc-noteref" in types -> Kind.SKIP
                types.any { it in NOTE_TYPES || it in NOTE_ROLES } -> Kind.NOTE
                else -> Kind.PLAIN
            }
        }
    }

    private enum class Kind { PLAIN, SKIP, NOTE }
}
