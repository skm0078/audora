// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/DocxParser.kt
// Purpose: Reads a Word document (.docx) into chapters of headings, paragraphs, list items and table rows (ADR-066).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome

/**
 * A .docx is a ZIP of XML parts; only the text parts are read ([isWanted]), never images or embedded files, and no
 * library is needed: the same forgiving [MarkupScanner] that reads EPUBs reads `word/document.xml`.
 *
 * Text is what sits in `w:t` runs, so deleted text (`w:delText`) and field codes (`w:instrText`) are not read, while
 * inserted text and field results are. A paragraph whose style is Title or Heading 1 (by the style's outline level or
 * name in `styles.xml`, so a German "Überschrift 1" counts too) starts a chapter; other headings pause like headings;
 * numbered and bulleted paragraphs are list items; a table row is read as its cells joined by "; ". Text boxes are
 * read where they are anchored, once (the drawing's fallback copy is skipped). Footnotes and endnotes go last.
 */
object DocxParser {
    private const val DOCUMENT = "word/document.xml"
    private const val STYLES = "word/styles.xml"
    private const val FOOTNOTES = "word/footnotes.xml"
    private const val ENDNOTES = "word/endnotes.xml"
    private const val CORE = "docProps/core.xml"
    private val WANTED = setOf(DOCUMENT, STYLES, FOOTNOTES, ENDNOTES, CORE)

    /** Whether a ZIP entry is one of the text parts this parser reads. */
    fun isWanted(path: String): Boolean = path in WANTED

    fun parse(entries: Map<String, ByteArray>): Outcome<TextDocument, TextBookError> {
        val main = entries[DOCUMENT] ?: return Outcome.Failure(TextBookError.Damaged("no word/document.xml"))
        val styles = entries[STYLES]?.let { WordStyles.from(scan(it)) } ?: WordStyles.NONE
        val builder = ChapterBuilder()
        WordWalk(styles, builder, asNotes = false).run(scan(main))
        listOf(FOOTNOTES, ENDNOTES).forEach { path ->
            entries[path]?.let { WordWalk(styles, builder, asNotes = true).run(scan(it)) }
        }
        val document = builder.build(entries[CORE]?.let { OfficeMeta.title(scan(it)) })
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }

    private fun scan(bytes: ByteArray): List<Markup> = MarkupScanner.scan(EpubParser.decode(bytes))
}

/** Heading levels by style id: 0 is Title or Heading 1 (a chapter), 1–8 smaller headings, null body text. */
internal class WordStyles private constructor(private val styles: Map<String, Style>) {
    private class Style(val name: String?, val outline: Int?, val basedOn: String?)

    fun level(styleId: String?): Int? = styleId?.let { levelOf(it, depth = 0) } ?: fallbackLevel(styleId)

    fun isList(styleId: String?): Boolean =
        styleId != null && styles[styleId]?.name?.lowercase()?.startsWith("list") == true

    /** The style's own level, else its name's, else the style it is based on (a chain cut off at [MAX_BASED_ON]). */
    private fun levelOf(id: String, depth: Int): Int? {
        val style = styles[id]?.takeIf { depth <= MAX_BASED_ON } ?: return null
        return style.outline?.takeIf { it in HEADING_LEVELS }
            ?: style.name?.let(::levelOfName)
            ?: style.basedOn?.let { levelOf(it, depth + 1) }
    }

    companion object {
        val NONE = WordStyles(emptyMap())
        private val HEADING_LEVELS = 0..8
        private const val MAX_BASED_ON = 10
        private val HEADING_NAME = Regex("""heading\s*([1-9])""")

        /** Word stores built-in style names in English whatever the language of the document ("heading 1"). */
        private fun levelOfName(name: String): Int? {
            val lower = name.lowercase()
            return if (lower == "title") 0 else HEADING_NAME.matchEntire(lower)?.groupValues?.get(1)?.toInt()?.minus(1)
        }

        /** Without styles.xml, the ids Word gives its built-in styles ("Heading1", "Title"). */
        private fun fallbackLevel(styleId: String?): Int? = styleId?.let(::levelOfName)

        fun from(markup: List<Markup>): WordStyles {
            val styles = HashMap<String, Style>()
            var id: String? = null
            var name: String? = null
            var outline: Int? = null
            var basedOn: String? = null
            for (token in markup) {
                when {
                    token is Markup.Start && token.name == "w:style" -> {
                        id = token.attributes["w:styleid"]
                        name = null
                        outline = null
                        basedOn = null
                    }

                    token is Markup.Start && token.name == "w:name" -> name = token.attributes["w:val"]

                    token is Markup.Start && token.name == "w:outlinelvl" ->
                        outline = token.attributes["w:val"]?.toIntOrNull()

                    token is Markup.Start && token.name == "w:basedon" -> basedOn = token.attributes["w:val"]

                    token is Markup.End && token.name == "w:style" -> {
                        id?.let { styles[it] = Style(name, outline, basedOn) }
                        id = null
                    }
                }
            }
            return WordStyles(styles)
        }
    }
}

/** One pass over a Word part's markup: paragraphs out to [builder], or all as notes when [asNotes]. */
private class WordWalk(
    private val styles: WordStyles,
    private val builder: ChapterBuilder,
    private val asNotes: Boolean,
) {
    private class Paragraph {
        var style: String? = null
        var outline: Int? = null
        var list = false
        val text = StringBuilder()
    }

    private val paragraphs = ArrayList<Paragraph>()
    private val tables = TableRows()
    private val skipped = ArrayList<String>()
    private var inText = 0
    private var inRun = 0

    fun run(markup: List<Markup>) {
        for (token in markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.End -> end(token.name)
                is Markup.Text -> text(token.text)
            }
        }
    }

    private fun text(text: String) {
        if (inText > 0 && skipped.isEmpty()) paragraphs.lastOrNull()?.text?.append(text)
    }

    @Suppress("CyclomaticComplexMethod") // One flat table of the Word elements that matter for reading.
    private fun start(tag: Markup.Start) {
        if (skipped.isNotEmpty() || isSkipped(tag)) {
            if (!tag.selfClosing) skipped += tag.name
            return
        }
        val paragraph = paragraphs.lastOrNull()
        when (tag.name) {
            "w:p" -> if (!tag.selfClosing) paragraphs += Paragraph()
            "w:pstyle" -> paragraph?.style = tag.attributes["w:val"]
            "w:outlinelvl" -> paragraph?.outline = tag.attributes["w:val"]?.toIntOrNull()
            "w:numpr" -> paragraph?.list = true
            "w:t" -> if (!tag.selfClosing) inText++
            "w:r" -> if (!tag.selfClosing) inRun++
            "w:tab", "w:br", "w:cr" -> if (inRun > 0) paragraph?.text?.append(' ')
            "w:nobreakhyphen" -> paragraph?.text?.append('-')
            "w:tbl" -> if (!tag.selfClosing) tables.openTable()
            "w:tr" -> if (!tag.selfClosing) tables.openRow()
            "w:tc" -> if (!tag.selfClosing) tables.openCell()
        }
    }

    private fun end(name: String) {
        if (skipped.isNotEmpty()) {
            if (skipped.last() == name) skipped.removeAt(skipped.lastIndex)
            return
        }
        when (name) {
            "w:p" -> paragraphs.removeLastOrNull()?.let(::emit)
            "w:t" -> inText = (inText - 1).coerceAtLeast(0)
            "w:r" -> inRun = (inRun - 1).coerceAtLeast(0)
            "w:tc" -> tables.closeCell()
            "w:tr" -> tables.closeRow()?.let(::emitRow)
            "w:tbl" -> tables.closeTable()
        }
    }

    private fun emitRow(row: String) = if (asNotes) builder.note(row) else builder.block(BlockKind.BODY, row)

    private fun emit(paragraph: Paragraph) {
        val text = paragraph.text.toString()
        val level = paragraph.outline?.takeIf { it in 0..MAX_HEADING_LEVEL } ?: styles.level(paragraph.style)
        when {
            tables.inTable -> tables.addToCell(text)
            asNotes -> builder.note(text)
            level == 0 -> builder.chapterHeading(text)
            level != null -> builder.block(BlockKind.HEADING, text)
            paragraph.list || styles.isList(paragraph.style) -> builder.block(BlockKind.LIST_ITEM, text)
            else -> builder.block(BlockKind.BODY, text)
        }
    }

    /** The fallback copy of a drawing (its text box is read once, from the choice), and separator "notes". */
    private fun isSkipped(tag: Markup.Start): Boolean = tag.name == "mc:fallback" ||
        (tag.name in NOTE_ELEMENTS && tag.attributes["w:type"] in SEPARATOR_TYPES)

    private companion object {
        const val MAX_HEADING_LEVEL = 8
        val NOTE_ELEMENTS = setOf("w:footnote", "w:endnote")
        val SEPARATOR_TYPES = setOf("separator", "continuationSeparator", "continuationNotice")
    }
}

/**
 * Table rows read as one block each, cells joined by "; " (as RI-4a reads PDF tables). A table inside a cell is
 * read into that cell, so only the outermost row makes a block.
 */
internal class TableRows {
    private class Row {
        val cells = ArrayList<String>()
        val cell = StringBuilder()
    }

    /** One list of rows per open table; the last row of each is the one being read. */
    private val tables = ArrayList<ArrayList<Row>>()

    val inTable: Boolean get() = tables.isNotEmpty()

    fun openTable() {
        tables += ArrayList<Row>()
    }

    fun openRow() {
        tables.lastOrNull()?.add(Row())
    }

    fun openCell() {
        currentRow()?.cell?.clear()
    }

    fun addToCell(text: String) {
        currentRow()?.cell?.append(text)?.append(' ')
    }

    fun closeCell() {
        val row = currentRow() ?: return
        row.cell.toString().trim().takeIf { it.isNotEmpty() }?.let(row.cells::add)
        row.cell.clear()
    }

    /** The finished row's text, when it is a row of the outermost table; an inner row joins its cell instead. */
    fun closeRow(): String? {
        val text = tables.lastOrNull()?.removeLastOrNull()?.cells?.joinToString(CELL_SEPARATOR)
            ?.takeIf { it.isNotBlank() }
        val outer = tables.getOrNull(tables.size - 2)
        outer?.lastOrNull()?.cell?.append(text.orEmpty())?.append(' ')
        return text.takeIf { outer == null }
    }

    fun closeTable() {
        tables.removeLastOrNull()
    }

    private fun currentRow(): Row? = tables.lastOrNull()?.lastOrNull()

    private companion object {
        const val CELL_SEPARATOR = "; "
    }
}

/** A document's title from its metadata: `dc:title` in Office and OpenDocument files, `<title>` in a web page. */
internal object OfficeMeta {
    fun title(markup: List<Markup>): String? = firstText(markup, "dc:title")

    /** The text right inside the first [name] element, when there is any. */
    fun firstText(markup: List<Markup>, name: String): String? {
        val at = markup.indexOfFirst { it is Markup.Start && it.name == name }
        if (at < 0) return null
        return (markup.getOrNull(at + 1) as? Markup.Text)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
