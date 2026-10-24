// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/ChapterBuilder.kt
// Purpose: Collects a document's blocks into chapters, notes last, for the Word, OpenDocument and Markdown readers.

package io.github.skm0078.audora.playback.text

/**
 * A chapter starts at each top-level heading ([chapterHeading]); text before the first one is a chapter of its own
 * with no title. Notes (footnotes, endnotes) go at the end of the last chapter, as in an EPUB (ADR-042). Whitespace
 * is collapsed and blocks without a letter or digit are dropped, as [XhtmlBlocks] does.
 */
internal class ChapterBuilder {
    private val chapters = ArrayList<TextChapter>()
    private val notes = ArrayList<TextBlock>()
    private var title: String? = null
    private var blocks = ArrayList<TextBlock>()

    fun block(kind: BlockKind, text: String) {
        clean(text)?.let { blocks += TextBlock(kind, it) }
    }

    /** A top-level heading: it ends the chapter before it and names the next. */
    fun chapterHeading(text: String) {
        val heading = clean(text) ?: return
        if (blocks.isNotEmpty()) chapters += TextChapter(title, blocks)
        blocks = ArrayList()
        title = heading
        blocks += TextBlock(BlockKind.HEADING, heading)
    }

    fun note(text: String) {
        clean(text)?.let { notes += TextBlock(BlockKind.BODY, it) }
    }

    fun build(documentTitle: String?, language: String? = null): TextDocument {
        val last = blocks + notes
        val all = if (last.isEmpty()) chapters else chapters + TextChapter(title, last)
        return TextDocument(documentTitle?.let(::clean), language, all)
    }

    private fun clean(text: String): String? = text.replace(WHITESPACE, " ").trim().takeIf { t ->
        t.any { it.isLetterOrDigit() }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
