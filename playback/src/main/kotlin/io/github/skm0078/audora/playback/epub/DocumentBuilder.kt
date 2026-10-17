// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/DocumentBuilder.kt
// Purpose: Groups a book's reading units into chapters, headings, paragraphs, lists, tables and notes.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.config.EpubConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * Pure and deterministic. The structure is what `:pdf` tagged ([ReadingUnit.kind]) and the pauses it set: a sentence
 * followed by [EpubConfig.paragraphPauseMs] or more ends its paragraph, list item or heading, and one that is not
 * carries on across a page turn. Units scored as junk ([EpubConfig.keepAtLeast]) are left out unless [voiced] says the
 * talking book reads them, so its text never misses a sentence its audio has.
 *
 * @param chapterTitle the title of a chapter with no heading ("Pages 1–12").
 */
class DocumentBuilder(
    private val config: EpubConfig = EpubConfig(),
    private val chapterTitle: (AudioChapter) -> String,
) {
    /**
     * @param units each page's units in reading order; a page missing from it (not readable) is left out.
     * @return null when no chapter has any text.
     */
    fun build(
        title: String,
        chapters: List<AudioChapter>,
        units: Map<Int, List<ReadingUnit>>,
        voiced: Set<UnitKey> = emptySet(),
    ): BookDocument? {
        val built = chapters.mapNotNull { chapter ->
            val kept = chapter.pages.flatMap { page -> units[page].orEmpty().filter { keeps(it, voiced) } }
            ChapterBuilder(chapter).apply { kept.forEach(::add) }.build()
        }
        val all = built.flatMap { it.sentences }.map { it.unit }
        if (all.isEmpty()) return null
        val byLang = all.groupingBy { it.lang }.fold(0) { chars, unit -> chars + unit.text.length }
        return BookDocument(title, byLang.maxBy { it.value }.key, byLang.keys.toCollection(LinkedHashSet()), built)
    }

    private fun keeps(unit: ReadingUnit, voiced: Set<UnitKey>): Boolean {
        val score = unit.speakScore
        return unit.text.isNotBlank() && (score == null || score >= config.keepAtLeast || UnitKey.of(unit) in voiced)
    }

    private fun endsParagraph(unit: ReadingUnit): Boolean = unit.pauseAfterMs >= config.paragraphPauseMs

    /** One chapter's blocks, built unit by unit. */
    private inner class ChapterBuilder(private val chapter: AudioChapter) {
        private val blocks = ArrayList<DocBlock>()
        private val notes = ArrayList<DocBlock.Note>()
        private var open: DocBlock? = null

        // Kept apart, so footnotes between two halves of a paragraph (a page or column turn) do not split it, and a
        // footnote that spills onto the next page joins its start.
        private var lastBody: ReadingUnit? = null
        private var lastNote: ReadingUnit? = null
        private var markedPage = -1

        fun add(unit: ReadingUnit) {
            if (unit.kind == UnitKind.FOOTNOTE) {
                addNote(DocSentence(unit))
                lastNote = unit
            } else {
                val sentence = DocSentence(unit, pageBreak = unit.page != markedPage)
                markedPage = unit.page
                open = grow(open, sentence) ?: start(sentence)
                lastBody = unit
            }
        }

        fun build(): DocChapter? {
            close()
            if (blocks.isEmpty() && notes.isEmpty()) return null
            val heading = blocks.firstOrNull { it is DocBlock.Heading }?.sentences?.joinToString(" ") { it.unit.text }
            val title = heading?.let(::shorten) ?: chapterTitle(chapter)
            return DocChapter(chapter.number, title, chapter.pages, blocks.toList(), notes.toList())
        }

        /** The open block with [sentence] added, or null when [sentence] starts a new block. */
        private fun grow(block: DocBlock?, sentence: DocSentence): DocBlock? {
            val unit = sentence.unit
            val goesOn = lastBody?.let { it.kind == unit.kind && !endsParagraph(it) } == true
            return when (block) {
                is DocBlock.Table -> block.takeIf { unit.kind == UnitKind.TABLE_ROW && sameTable(it, unit) }
                    ?.let { it.copy(rows = it.rows + sentence) }

                is DocBlock.ListItems -> block.takeIf { unit.kind == UnitKind.LIST_ITEM }
                    ?.with(sentence, newItem = !goesOn)

                is DocBlock.Heading -> block.takeIf { unit.kind == UnitKind.HEADING && goesOn }
                    ?.let { it.copy(sentences = it.sentences + sentence) }

                is DocBlock.Paragraph -> block.takeIf { unit.kind == it.kind && goesOn }
                    ?.let { it.copy(sentences = it.sentences + sentence) }

                is DocBlock.Note, null -> null
            }
        }

        private fun DocBlock.ListItems.with(sentence: DocSentence, newItem: Boolean): DocBlock.ListItems =
            if (newItem) {
                copy(items = items + listOf(listOf(sentence)))
            } else {
                copy(items = items.dropLast(1) + listOf(items.last() + sentence))
            }

        private fun start(sentence: DocSentence): DocBlock {
            close()
            return when (sentence.unit.kind) {
                UnitKind.HEADING -> DocBlock.Heading(listOf(sentence))
                UnitKind.LIST_ITEM -> DocBlock.ListItems(listOf(listOf(sentence)))
                UnitKind.TABLE_SUMMARY -> DocBlock.Table(header = sentence, rows = emptyList())
                UnitKind.TABLE_ROW -> DocBlock.Table(header = null, rows = listOf(sentence))
                else -> DocBlock.Paragraph(listOf(sentence), sentence.unit.kind)
            }
        }

        private fun close() {
            open?.let(blocks::add)
            open = null
        }

        private fun sameTable(table: DocBlock.Table, unit: ReadingUnit): Boolean {
            val first = table.sentences.first().unit
            return first.page == unit.page && first.table?.tableIndex == unit.table?.tableIndex
        }

        /** A note starts at its number, or after the previous note's last sentence ended its paragraph. */
        private fun addNote(sentence: DocSentence) {
            val previous = notes.lastOrNull()
            val goesOn = sentence.unit.noteRef == null && lastNote?.let { !endsParagraph(it) } == true
            if (previous != null && goesOn) {
                notes[notes.lastIndex] = DocBlock.Note(previous.sentences + sentence)
            } else {
                notes.add(DocBlock.Note(listOf(sentence)))
            }
        }

        private fun shorten(text: String): String =
            if (text.length <= config.chapterTitleMaxChars) text else text.take(config.chapterTitleMaxChars - 1) + "…"
    }
}
