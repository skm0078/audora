// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/Fb2Parser.kt
// Purpose: Reads a FictionBook (.fb2) into chapters of headings, paragraphs, verse and notes
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome

/**
 * FB2 is one XML file: `description/title-info` holds the title, authors and language; the first `body` holds the
 * book as nested `section`s, each with an optional `title`; a `body name="notes"` (or "comments") holds the notes.
 * A top-level section starts a chapter named by its title, deeper section titles and `subtitle`s are headings,
 * paragraphs (`p`), verse lines (`v`), epigraph authors and table rows (cells joined by "; ") are blocks. Notes go
 * last with their numbers left out, and so do the "[1]" note links in the text. Pictures (`binary`) are never read.
 */
object Fb2Parser {
    /** The entry of a zipped FB2 (.fb2.zip) that holds the book. */
    fun isWanted(path: String): Boolean = path.lowercase().endsWith(".fb2")

    /** A zipped FB2's one book entry, or Damaged when the ZIP holds none. */
    fun parseZip(entries: Map<String, ByteArray>): Outcome<TextDocument, TextBookError> =
        entries.entries.firstOrNull { isWanted(it.key) }?.let { parse(it.value) }
            ?: Outcome.Failure(TextBookError.Damaged("no .fb2 in the zip"))

    fun parse(bytes: ByteArray): Outcome<TextDocument, TextBookError> {
        val markup = MarkupScanner.scan(EpubParser.decode(bytes))
        val info = Fb2Info.of(markup)
        val builder = ChapterBuilder()
        Fb2Walk(builder).run(markup)
        val document = builder.build(info.title, info.language)
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }
}

/** What `description/title-info` says: the book's title, its authors' names and its language. */
data class Fb2Info(val title: String?, val author: String?, val language: String?) {
    companion object {
        private val NAME_PARTS = listOf("first-name", "middle-name", "last-name")

        fun of(markup: List<Markup>): Fb2Info {
            val info = Fb2InfoWalk()
            for (token in markup) {
                if (!info.take(token)) break
            }
            return Fb2Info(
                title = info.texts["book-title"]?.firstOrNull(),
                author = info.authors.mapNotNull { parts ->
                    NAME_PARTS.mapNotNull { parts[it] }.joinToString(" ").ifBlank { parts["nickname"] }
                }.joinToString(", ").ifBlank { null },
                language = info.texts["lang"]?.firstOrNull(),
            )
        }
    }
}

/** Collects `title-info`'s fields; stops (false) at the end of the description, before the book's text. */
private class Fb2InfoWalk {
    val texts = HashMap<String, MutableList<String>>()
    val authors = ArrayList<MutableMap<String, String>>()
    private val open = ArrayList<String>()

    fun take(token: Markup): Boolean {
        when (token) {
            is Markup.Start -> if (!token.selfClosing) {
                open += token.localName
                if (token.localName == "author" && "title-info" in open) authors += HashMap()
            }

            is Markup.End -> {
                if (token.localName == "description" || token.localName == "body") return false
                val index = open.lastIndexOf(token.localName)
                if (index >= 0) while (open.size > index) open.removeAt(open.lastIndex)
            }

            is Markup.Text -> collect(token.text.trim())
        }
        return true
    }

    private fun collect(text: String) {
        if (text.isEmpty() || "title-info" !in open) return
        val field = open.last()
        if ("author" in open) {
            authors.lastOrNull()?.put(field, text)
        } else {
            texts.getOrPut(field) { ArrayList() } += text
        }
    }
}

private class Fb2Walk(private val builder: ChapterBuilder) {
    private val open = ArrayList<String>()
    private val buffer = StringBuilder()
    private val row = ArrayList<String>()
    private var skipped = 0
    private var inNotes = false
    private var sectionDepth = 0

    fun run(markup: List<Markup>) {
        for (token in markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.End -> end(token.localName)
                is Markup.Text -> if (skipped == 0 && open.isNotEmpty()) buffer.append(token.text)
            }
        }
    }

    private fun start(tag: Markup.Start) {
        val name = tag.localName
        if (skipped > 0 || isSkipped(tag)) {
            if (!tag.selfClosing) {
                skipped++
                open += name
            }
            return
        }
        when (name) {
            "body" -> inNotes = tag.attributes["name"]?.lowercase() in NOTE_BODIES
            "section" -> if (!tag.selfClosing) sectionDepth++
            "empty-line" -> buffer.append(' ')
        }
        if (name in BLOCKS) flush()
        if (!tag.selfClosing) open += name
    }

    private fun end(name: String) {
        val index = open.lastIndexOf(name)
        if (index < 0) return
        if (skipped > 0) {
            skipped -= open.size - index
            while (open.size > index) open.removeAt(open.lastIndex)
            return
        }
        when (name) {
            "title" -> emitTitle()
            "td", "th" -> cell()
            "tr" -> emitRow()
            in BLOCKS -> flush()
        }
        if (name == "section") sectionDepth = (sectionDepth - 1).coerceAtLeast(0)
        while (open.size > index) open.removeAt(open.lastIndex)
    }

    /** The description, pictures, stylesheets, a note's own title (its number) and the note links in the text. */
    private fun isSkipped(tag: Markup.Start): Boolean = tag.localName in SKIPPED ||
        (tag.localName == "title" && inNotes) ||
        (tag.localName == "a" && tag.attributes["type"]?.lowercase() == "note")

    /** A paragraph-like element ended: its text is a block, in a title a part of the heading, in a cell a cell. */
    private fun flush() {
        if ("title" in open || "td" in open || "th" in open) {
            buffer.append(' ')
            return
        }
        val text = buffer.toString()
        buffer.clear()
        when {
            inNotes -> builder.note(text)
            open.lastOrNull() == "subtitle" -> builder.block(BlockKind.HEADING, text)
            else -> builder.block(BlockKind.BODY, text)
        }
    }

    /** A title: a top-level section's starts a chapter; the body's, a poem's and deeper sections' are headings. */
    private fun emitTitle() {
        val text = buffer.toString()
        buffer.clear()
        val parent = open.getOrNull(open.lastIndexOf("title") - 1)
        if (sectionDepth == 1 && parent == "section") {
            builder.chapterHeading(text)
        } else {
            builder.block(BlockKind.HEADING, text)
        }
    }

    private fun cell() {
        row += buffer.toString().trim()
        buffer.clear()
    }

    private fun emitRow() {
        val text = row.filter { it.isNotEmpty() }.joinToString(CELL_SEPARATOR)
        row.clear()
        buffer.clear()
        if (inNotes) builder.note(text) else builder.block(BlockKind.BODY, text)
    }

    private companion object {
        const val CELL_SEPARATOR = "; "

        /** Elements whose end closes a block; a title's or cell's paragraphs are collected until it ends. */
        val BLOCKS = setOf("p", "v", "subtitle", "text-author", "section", "poem", "stanza", "epigraph", "cite", "body")
        val SKIPPED = setOf("description", "binary", "stylesheet", "image")
        val NOTE_BODIES = setOf("notes", "comments", "footnotes")
    }
}
