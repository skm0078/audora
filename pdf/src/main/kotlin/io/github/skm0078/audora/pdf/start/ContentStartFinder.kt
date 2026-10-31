// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/start/ContentStartFinder.kt
// Purpose: Finds the page a new book's reading starts on, past its contents, copyright and preface pages: from the
//          book's chapter list when it has one, else from its first pages' text (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.pdf.start

import io.github.skm0078.audora.core.config.ContentStartConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.document.OutlineEntry

/**
 * Where reading starts. [page] is zero-based and above 0 (a book that starts on its first page has no
 * [ContentStart]); [title] is the chapter's own name when one is known: the book's text, shown and never logged.
 * [skipped] names what the pages before it were, as far as the search could tell; empty when the chapter list only
 * said where chapter 1 is.
 */
data class ContentStart(val page: Int, val title: String?, val skipped: Set<FrontMatterKind>, val source: Source) {
    enum class Source { CHAPTER_LIST, PAGES }

    init {
        require(page > 0) { "a content start is after the first page, was $page" }
    }

    override fun toString() = "ContentStart(page=$page, titled=${title != null}, skipped=$skipped, source=$source)"
}

/** How the search ended, for the log: what it found, or why the book opens on its first page. */
sealed interface StartSearch {
    data class Found(val start: ContentStart) : StartSearch

    /** The book's reading starts on its first page, or the search could not tell (and so skips nothing). */
    data class FirstPage(val why: Why, val pagesRead: Int) : StartSearch

    enum class Why { SHORT_DOCUMENT, STARTS_AT_ONCE, NOT_SURE, SCANNED_PAGE, OUT_OF_TIME }
}

/**
 * Never guesses: when it cannot tell, the book opens on its first page as it always did. Pages are read one at a
 * time from the first and the search stops at the first page that starts the reading, so a book whose front matter
 * is three pages long costs three page reads.
 */
class ContentStartFinder(private val config: ContentStartConfig = ContentStartConfig()) {
    private val pages = FrontMatterPage(config)

    /**
     * [readPage] returns a page's sentences, or null when it cannot be read without waiting (a scanned page not yet
     * recognised): the search never starts OCR. [nowMs] measures [ContentStartConfig.budgetMs].
     */
    suspend fun find(
        outline: List<OutlineEntry>,
        pageCount: Int,
        nowMs: () -> Long,
        readPage: suspend (Int) -> List<ReadingUnit>?,
    ): StartSearch {
        if (pageCount < config.minBookPages) return StartSearch.FirstPage(StartSearch.Why.SHORT_DOCUMENT, 0)
        return fromOutline(outline, pageCount) ?: fromPages(pageCount, nowMs, readPage)
    }

    /**
     * The first chapter-list entry that is not front matter. A wrapper entry (the book's title around everything)
     * holding front matter is looked into rather than taken. Null when the list has nothing to go by, so the pages
     * are searched instead.
     */
    fun fromOutline(outline: List<OutlineEntry>, pageCount: Int): StartSearch? {
        val skipped = LinkedHashSet<FrontMatterKind>()
        val first = firstContent(outline, pageCount, skipped)
        val page = first?.page
        return when {
            first == null || page == null || page > config.lastOutlineStart(pageCount) -> null
            page == 0 -> StartSearch.FirstPage(StartSearch.Why.STARTS_AT_ONCE, 0)
            else -> StartSearch.Found(ContentStart(page, first.title.trim().ifEmpty { null }, skipped, CHAPTER_LIST))
        }
    }

    private fun firstContent(
        level: List<OutlineEntry>,
        pageCount: Int,
        skipped: MutableSet<FrontMatterKind>,
    ): OutlineEntry? {
        for (entry in level) {
            val kind = FrontMatterWords.kindOf(entry.title)
            if (kind != null) {
                skipped += kind
                continue
            }
            val found = when {
                entry.children.any(::holdsFrontMatter) -> firstContent(entry.children, pageCount, skipped)
                entry.page != null && entry.page in 0 until pageCount -> entry
                else -> firstContent(entry.children, pageCount, skipped)
            }
            if (found != null) return found
        }
        return null
    }

    private fun holdsFrontMatter(entry: OutlineEntry): Boolean =
        FrontMatterWords.kindOf(entry.title) != null || entry.children.any(::holdsFrontMatter)

    private suspend fun fromPages(
        pageCount: Int,
        nowMs: () -> Long,
        readPage: suspend (Int) -> List<ReadingUnit>?,
    ): StartSearch {
        val deadline = nowMs() + config.budgetMs
        val walk = FrontMatterWalk()
        val limit = config.pagesToSearch(pageCount)
        for (page in 0 until limit) {
            val outOfTime = nowMs() > deadline
            val ended = if (outOfTime) StartSearch.FirstPage(StartSearch.Why.OUT_OF_TIME, page) else null
            val result = ended ?: step(page, walk, readPage)
            if (result != null) return result
        }
        return StartSearch.FirstPage(StartSearch.Why.NOT_SURE, limit)
    }

    /** What [page] says: the start, the end of the search, or (null) front matter to read past. */
    private suspend fun step(
        page: Int,
        walk: FrontMatterWalk,
        readPage: suspend (Int) -> List<ReadingUnit>?,
    ): StartSearch? {
        val units = readPage(page)
        val look = units?.let(pages::look)
        val title = look?.let(walk::startTitle)
        return when {
            units == null -> StartSearch.FirstPage(StartSearch.Why.SCANNED_PAGE, page)
            page == 0 && (title != null || look.startsTheBook(units)) -> StartSearch.FirstPage(STARTS_AT_ONCE, 1)
            title == null -> null
            else -> StartSearch.Found(ContentStart(page, title.value, walk.skipped.toSet(), PAGES))
        }
    }

    /** A first page with a front-matter heading and running text under it starts the book (see hasRunningText). */
    private fun PageLook?.startsTheBook(units: List<ReadingUnit>) =
        (this is PageLook.FrontHeading || this is PageLook.Prose) && pages.hasRunningText(units)

    private companion object {
        val STARTS_AT_ONCE = StartSearch.Why.STARTS_AT_ONCE
        val CHAPTER_LIST = ContentStart.Source.CHAPTER_LIST
        val PAGES = ContentStart.Source.PAGES
    }
}

/** A start's title, which may be unknown (null [value]) even though the start itself is found. */
@JvmInline
value class StartTitle(val value: String?)

/**
 * The page-by-page state: what was skipped so far and which front-matter section is running on. Only a preface or
 * acknowledgements run on: their pages of plain text stay front matter until a page opens with a heading of its own.
 * A contents or copyright page never runs on (a second contents page is told by its page numbers).
 */
internal class FrontMatterWalk {
    val skipped = LinkedHashSet<FrontMatterKind>()
    private var section: FrontMatterKind? = null

    /** The start's title when this page starts the reading; null when it is front matter. */
    fun startTitle(look: PageLook): StartTitle? {
        val title = when (look) {
            is PageLook.Chapter -> StartTitle(look.title)
            is PageLook.Prose -> look.heading?.let(::StartTitle) ?: StartTitle(null).takeIf { section == null }
            else -> null
        }
        if (title == null) skip(look)
        return title
    }

    private fun skip(look: PageLook) {
        when (look) {
            PageLook.Short -> if (section == null) skipped += FrontMatterKind.TITLE_PAGES
            PageLook.Contents -> enter(FrontMatterKind.CONTENTS)
            PageLook.Copyright -> enter(FrontMatterKind.COPYRIGHT)
            is PageLook.FrontHeading -> enter(look.kind)
            is PageLook.Chapter, is PageLook.Prose -> Unit
        }
    }

    private fun enter(kind: FrontMatterKind) {
        skipped += kind
        section = kind.takeIf { it in RUNS_ON }
    }

    private companion object {
        val RUNS_ON = setOf(FrontMatterKind.PREFACE, FrontMatterKind.THANKS)
    }
}
