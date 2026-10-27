// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/djvu/DjvuParser.kt
// Purpose: A DjVu with a text layer as a text book: chapters from its outline, blocks from each page's hidden text,
//          and its title from its metadata (ADR 2026-10-27-djvu-books).

package io.github.skm0078.audora.playback.djvu

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.text.TextBlock
import io.github.skm0078.audora.playback.text.TextBookError
import io.github.skm0078.audora.playback.text.TextChapter
import io.github.skm0078.audora.playback.text.TextDocument

/** A DjVu's pages and outline as s-expression text: the native [DjvuFile], or a test's fixture strings. */
interface DjvuPages {
    val pageCount: Int

    /** Page [page] (from 0)'s hidden text; empty when it has none. */
    fun pageText(page: Int): String

    fun outline(): String

    fun annotations(): String
}

object DjvuParser {
    /** A page with at least this many letters has a text layer worth reading. */
    private const val MIN_PAGE_LETTERS = 20

    /** Pages looked at to tell a text-layer DjVu from a scan, spread across the book. */
    private const val SAMPLE_PAGES = 20

    /**
     * The book's text, a chapter per outline entry (from the page it points at to the next entry's page), or one
     * untitled chapter without an outline. TooLarge past [maxTextChars] characters; NoText when no page has text.
     * Throws [DjvuExpressionException] for a text layer that is not well formed.
     */
    fun parse(pages: DjvuPages, maxTextChars: Long): Outcome<TextDocument, TextBookError> {
        val starts = chapterStarts(pages.outline(), pages.pageCount)
        val chapters = ArrayList<TextChapter>()
        var total = 0L
        starts.forEachIndexed { index, (title, start) ->
            val end = starts.getOrNull(index + 1)?.second ?: pages.pageCount
            val blocks = ArrayList<TextBlock>()
            for (page in start until end) {
                val pageBlocks = DjvuPageText.blocks(DjvuExpressions.parse(pages.pageText(page)))
                total += pageBlocks.sumOf { it.text.length }
                if (total > maxTextChars) return Outcome.Failure(TextBookError.TooLarge("DjVu text over the limit"))
                blocks += pageBlocks
            }
            if (blocks.isNotEmpty()) chapters += TextChapter(title, blocks)
        }
        val document = TextDocument(metadata(pages.annotations(), "title"), language = null, chapters = chapters)
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }

    /**
     * True when at least half of up to [SAMPLE_PAGES] pages, spread over the book, have a text layer: then it is read
     * as text, else its pages are pictures for OCR. Covers and blank pages are why it is not "any page".
     */
    fun hasTextLayer(pages: DjvuPages): Boolean {
        val count = pages.pageCount
        if (count <= 0) return false
        val sampled = (0 until minOf(count, SAMPLE_PAGES)).map { it * count / minOf(count, SAMPLE_PAGES) }.distinct()
        val withText = sampled.count { page ->
            val blocks = DjvuPageText.blocks(DjvuExpressions.parse(pages.pageText(page)))
            blocks.sumOf { block -> block.text.count { it.isLetter() } } >= MIN_PAGE_LETTERS
        }
        return withText * 2 >= sampled.size
    }

    /**
     * The document's `(metadata (key "value"))` value, such as its title or author; null when it has none. The
     * annotations come as one list of them (the native bridge) or one after another (`djvused print-ant`).
     */
    fun metadata(annotations: String, key: String): String? = DjvuExpressions.parseAll(annotations)
        .filterIsInstance<DjvuExpression.Items>()
        .flatMap { if (it.head == null) it.items else listOf(it) }
        .filterIsInstance<DjvuExpression.Items>()
        .filter { it.head == "metadata" }
        .flatMap { it.items.filterIsInstance<DjvuExpression.Items>() }
        .firstOrNull { it.head == key }
        ?.let { (it.items.getOrNull(1) as? DjvuExpression.Text)?.value?.trim() }
        ?.takeIf { it.isNotEmpty() }

    /**
     * (title, first page) of each chapter, in page order: the outline's entries at every depth, which point at a page
     * as "#12" (the 12th); an entry pointing at a page by name, or out of the book, is skipped. Pages before the first
     * entry are an untitled chapter of their own.
     */
    private fun chapterStarts(outline: String, pageCount: Int): List<Pair<String?, Int>> {
        val entries = ArrayList<Pair<String?, Int>>()
        (DjvuExpressions.parse(outline) as? DjvuExpression.Items)?.let { entries(it, pageCount, entries) }
        val sorted = entries.sortedBy { it.second }.distinctBy { it.second }
        return when {
            sorted.isEmpty() -> listOf(null to 0)
            sorted.first().second > 0 -> listOf<Pair<String?, Int>>(null to 0) + sorted
            else -> sorted
        }
    }

    private fun entries(list: DjvuExpression.Items, pageCount: Int, into: MutableList<Pair<String?, Int>>) {
        for (item in list.items.filterIsInstance<DjvuExpression.Items>()) {
            val title = (item.items.getOrNull(0) as? DjvuExpression.Text)?.value?.trim()?.takeIf { it.isNotEmpty() }
            val target = (item.items.getOrNull(1) as? DjvuExpression.Text)?.value
            val page = target?.removePrefix("#")?.toIntOrNull()?.minus(1)
            if (title != null && page != null && page in 0 until pageCount) into += title to page
            entries(item, pageCount, into)
        }
    }
}
