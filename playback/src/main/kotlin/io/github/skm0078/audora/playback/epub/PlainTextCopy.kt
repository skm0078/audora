// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/PlainTextCopy.kt
// Purpose: Writes a copy as plain UTF-8 text that any app, screen reader or braille display can use (plan item 69).

package io.github.skm0078.audora.playback.epub

/**
 * Paragraphs separated by a blank line, headings underlined, list items bulleted, table rows as cells joined by
 * " | " (header first), notes at the end of their chapter. "[Page 12]" lines mark printed pages; a page that starts
 * inside a paragraph is marked before that paragraph.
 */
internal class PlainTextCopy(private val labels: CopyLabels = CopyLabels()) {
    fun write(doc: BookDocument): String = buildString {
        underlined(doc.title, DOUBLE)
        doc.chapters.forEach { chapter ->
            chapter.blocks.forEach { block(it) }
            if (chapter.notes.isNotEmpty()) {
                underlined(labels.notes, SINGLE)
                chapter.notes.forEach { note -> paragraph(note.sentences) }
            }
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.block(block: DocBlock) {
        pageMarks(block.sentences)
        when (block) {
            is DocBlock.Heading -> underlined(text(block.sentences), SINGLE)

            is DocBlock.ListItems -> {
                block.items.forEach { append(BULLET).append(item(it)).append('\n') }
                append('\n')
            }

            is DocBlock.Table -> {
                block.sentences.forEach { append(it.cells().joinToString(CELL_SEPARATOR)).append('\n') }
                append('\n')
            }

            is DocBlock.Paragraph, is DocBlock.Note -> paragraph(block.sentences)
        }
    }

    private fun StringBuilder.pageMarks(sentences: List<DocSentence>) {
        sentences.filter { it.pageBreak }.forEach { append("[Page ").append(it.unit.page + 1).append("]\n\n") }
    }

    private fun StringBuilder.paragraph(sentences: List<DocSentence>) {
        append(text(sentences)).append("\n\n")
    }

    private fun StringBuilder.underlined(text: String, rule: Char) {
        append(text).append('\n').append(rule.toString().repeat(text.length.coerceAtLeast(1))).append("\n\n")
    }

    private fun item(sentences: List<DocSentence>): String = text(sentences).trimStart { it in PRINTED_BULLETS }.trim()

    private fun text(sentences: List<DocSentence>): String = sentences.joinToString(" ") { it.unit.text }

    private companion object {
        const val DOUBLE = '='
        const val SINGLE = '-'
        const val BULLET = "• "
        const val CELL_SEPARATOR = " | "

        /** Bullets the PDF printed, replaced by [BULLET] so an item never reads "• • item". */
        const val PRINTED_BULLETS = "•◦▪‣●○■-*"
    }
}
