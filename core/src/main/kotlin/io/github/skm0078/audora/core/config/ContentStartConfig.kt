// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ContentStartConfig.kt
// Purpose: How far and how long a new book is searched for where its reading starts, past the contents, copyright
//          and preface pages (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.core.config

/**
 * @property minBookPages a shorter document (an article, a report, a declaration) is read from its first page: its
 *   front matter, when it has any, is a line or two, and its "Preamble" or "মুখবন্ধ" is the text itself.
 * @property maxPages at most this many pages are read to find the start, whatever the book's length, so a 300-page
 *   book costs what a 30-page one does. Front matter longer than this is not skipped (the book opens on page 1).
 * @property maxShare nor more than this share of the book: a start found later is more likely a wrong guess than
 *   front matter that long.
 * @property outlineMaxShare a chapter list (outline) is the book's own word, so its first chapter may sit further in.
 * @property budgetMs the page search gives up after this long and the book opens on page 1, so a slow phone never
 *   waits long for its first page. The chapter-list step reads no pages and has no budget.
 * @property proseWords a page with at least this many words is running text: the start, unless a preface runs on.
 * @property contentsNumbers with [contentsNumberShare]: a page with at least this many page numbers, making up at
 *   least that share of its words, is a contents page ("Chapter 1 … 13").
 * @property copyrightCues a page with this many different copyright-page words ("ISBN", "All rights reserved",
 *   "प्रकाशक", "প্রথম প্রকাশ") is the copyright page, and [copyrightMaxWords] keeps a chapter that names a publisher
 *   from being taken for one.
 * @property headingWords a first line of at most this many words can be a heading ("Preface", "অধ্যায় ১").
 */
data class ContentStartConfig(
    val minBookPages: Int = DEFAULT_MIN_BOOK_PAGES,
    val maxPages: Int = DEFAULT_MAX_PAGES,
    val maxShare: Float = DEFAULT_MAX_SHARE,
    val outlineMaxShare: Float = DEFAULT_OUTLINE_MAX_SHARE,
    val budgetMs: Long = DEFAULT_BUDGET_MS,
    val proseWords: Int = DEFAULT_PROSE_WORDS,
    val contentsNumbers: Int = DEFAULT_CONTENTS_NUMBERS,
    val contentsNumberShare: Float = DEFAULT_CONTENTS_NUMBER_SHARE,
    val copyrightCues: Int = DEFAULT_COPYRIGHT_CUES,
    val copyrightMaxWords: Int = DEFAULT_COPYRIGHT_MAX_WORDS,
    val headingWords: Int = DEFAULT_HEADING_WORDS,
) {
    init {
        require(minBookPages >= 2) { "minBookPages must be >= 2, was $minBookPages" }
        require(maxPages >= 1) { "maxPages must be >= 1, was $maxPages" }
        require(maxShare in 0f..1f && outlineMaxShare in 0f..1f) {
            "shares must be within 0..1, were $maxShare and $outlineMaxShare"
        }
        require(budgetMs >= 0) { "budgetMs must not be negative, was $budgetMs" }
        require(proseWords >= 1 && headingWords >= 1) { "word counts must be >= 1" }
        require(contentsNumbers >= 1 && contentsNumberShare in 0f..1f) { "contents thresholds out of range" }
        require(copyrightCues >= 1 && copyrightMaxWords >= 1) { "copyright thresholds must be >= 1" }
    }

    /** The pages the page search may read in a [pageCount]-page book: none in a short document. */
    fun pagesToSearch(pageCount: Int): Int =
        if (pageCount < minBookPages) 0 else minOf(maxPages, (pageCount * maxShare).toInt(), pageCount)

    /** The last page a chapter list's first chapter may start on and still be skipped to. */
    fun lastOutlineStart(pageCount: Int): Int =
        if (pageCount < minBookPages) 0 else (pageCount * outlineMaxShare).toInt()

    companion object {
        const val DEFAULT_MIN_BOOK_PAGES = 20
        const val DEFAULT_MAX_PAGES = 30
        const val DEFAULT_MAX_SHARE = 0.34f
        const val DEFAULT_OUTLINE_MAX_SHARE = 0.5f
        const val DEFAULT_BUDGET_MS = 2_500L
        const val DEFAULT_PROSE_WORDS = 60
        const val DEFAULT_CONTENTS_NUMBERS = 6
        const val DEFAULT_CONTENTS_NUMBER_SHARE = 0.12f
        const val DEFAULT_COPYRIGHT_CUES = 2
        const val DEFAULT_COPYRIGHT_MAX_WORDS = 450
        const val DEFAULT_HEADING_WORDS = 8
    }
}
