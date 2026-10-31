// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookStart.kt
// Purpose: Where a book never read before opens: past its contents, copyright and preface pages, found once and
//          kept, and what the reader says about the pages it skipped (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.start.ContentStartFinder
import io.github.skm0078.audora.pdf.start.FrontMatterKind
import io.github.skm0078.audora.pdf.start.StartSearch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The book opened at [page] (zero-based), so pages 1 to [page] were passed over. [title] is the chapter's own name
 * when known (the book's text: shown, never logged); [skipped] what those pages were, as far as the search could tell.
 */
data class SkippedFrontMatter(val page: Int, val title: String?, val skipped: Set<FrontMatterKind>) {
    override fun toString() = "SkippedFrontMatter(page=$page, titled=${title != null}, skipped=$skipped)"
}

/** The reader's way to the start of a new book; [None] opens every book on its first page (tests, previews). */
interface BookStart {
    /** Where book [id] opens when nothing in it was read yet; null for its first page. */
    suspend fun find(id: BookId, document: ReaderDocument): SkippedFrontMatter?

    /** "Read from the start": this book opens on its first page from now on. */
    suspend fun readFromStart(id: BookId)

    companion object {
        val None = object : BookStart {
            override suspend fun find(id: BookId, document: ReaderDocument): SkippedFrontMatter? = null

            override suspend fun readFromStart(id: BookId) = Unit
        }
    }
}

/** The switch in Settings and each book's start, once found. Called off the main thread. */
interface BookStartStore {
    fun skipFrontMatter(): Boolean

    fun setSkipFrontMatter(on: Boolean)

    /** The start kept for [id]: null when never searched, page 0 when it opens on its first page. */
    fun saved(id: BookId): SkippedFrontMatter?

    fun save(id: BookId, start: SkippedFrontMatter)
}

/**
 * Searches once per book and keeps the answer, found or not, so opening a book again (browsing the library) never
 * pays twice. With the switch off nothing is searched and a kept start is not used.
 */
class FrontMatterBookStart(
    private val store: BookStartStore,
    private val finder: ContentStartFinder = ContentStartFinder(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : BookStart {
    override suspend fun find(id: BookId, document: ReaderDocument): SkippedFrontMatter? {
        val (on, kept) = withContext(io) { store.skipFrontMatter() to store.saved(id) }
        val start = when {
            !on -> null
            kept != null -> kept
            else -> search(id, document).also { found -> withContext(io) { store.save(id, found ?: FIRST_PAGE) } }
        }
        return start?.takeIf { it.page in 1 until document.pageCount }
    }

    override suspend fun readFromStart(id: BookId) = withContext(io) { store.save(id, FIRST_PAGE) }

    private suspend fun search(id: BookId, document: ReaderDocument): SkippedFrontMatter? {
        val began = nowMs()
        val search = finder.find(document.outline(), document.pageCount, nowMs) { page ->
            // Never starts OCR: a scanned page not recognised yet ends the search, and the book opens on page 1.
            document.pageIfReady(page).getOrNull()?.units
        }
        AudoraLog.i(TAG, "Start search, book=${id.logLabel} pages=${document.pageCount} $search ms=${nowMs() - began}")
        val found = (search as? StartSearch.Found)?.start ?: return null
        return SkippedFrontMatter(found.page, found.title, found.skipped)
    }

    private companion object {
        const val TAG = "Audora.BookStart"
        val FIRST_PAGE = SkippedFrontMatter(0, null, emptySet())
    }
}
