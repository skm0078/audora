// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/XhtmlBlocks.kt
// Purpose: Turns one EPUB content document (XHTML) into headings, paragraphs and list items, notes last, and says
//          which block each `id` falls in, so a contents link to `chapter.xhtml#part2` finds its place.

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

    fun blocks(markup: List<Markup>): List<TextBlock> = read(markup).blocks

    /** The blocks, and each `id` (or `<a name>`) in the reading text with the place it marks ([Anchor]). */
    fun read(markup: List<Markup>): XhtmlDocument {
        val state = Walk()
        for (token in markup) {
            when (token) {
                is Markup.Start -> state.start(token)
                is Markup.End -> state.end(token.localName)
                is Markup.Text -> state.text(token.text)
            }
        }
        state.flush()
        return XhtmlDocument(state.body + state.notes, state.body.size, state.anchors)
    }

    /** The first heading's text, for a chapter the contents file does not name. */
    fun firstHeading(blocks: List<TextBlock>): String? = blocks.firstOrNull { it.kind == BlockKind.HEADING }?.text

    private class Walk {
        val body = ArrayList<TextBlock>()
        val notes = ArrayList<TextBlock>()
        val anchors = HashMap<String, Anchor>()
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
            val kind = kindOf(name, tag.attributes)
            anchor(tag, kind)
            if (tag.selfClosing) return
            open += name to kind
        }

        /**
         * An `id` in the reading text marks the block being collected (or the next one, if none is), at the
         * characters collected so far. Ids inside notes and skipped parts mark nothing a listener reaches.
         */
        private fun anchor(tag: Markup.Start, kind: Kind) {
            val id = tag.attributes["id"] ?: tag.attributes["name"]?.takeIf { tag.localName == "a" } ?: return
            if (kind != Kind.PLAIN || skipping() || open.any { it.second == Kind.NOTE }) return
            val collected = buffer.toString().replace(WHITESPACE, " ").trimStart()
            anchors.putIfAbsent(id, Anchor(body.size, collected.length))
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

/**
 * Where an `id` sits: [block] is an index into the document's body blocks (equal to their count when the id comes
 * after the last of them), and [char] a character offset into that block's text.
 */
data class Anchor(val block: Int, val char: Int)

/** One content document read by [XhtmlBlocks.read]: the first [bodyBlocks] of [blocks] are its text, the rest notes. */
class XhtmlDocument(val blocks: List<TextBlock>, val bodyBlocks: Int, val anchors: Map<String, Anchor>)
