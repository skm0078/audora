// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/search/SearchIndex.kt
// Purpose: One book's search index (plan item 16): sentences by page, how far it is built, and term queries.

package io.github.skm0078.audora.playback.search

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import java.io.Closeable

/** A sentence that matched: where it is (for the reader to go there) and its text (shown only on screen). */
data class SearchHit(val page: Int, val charStart: Int, val text: String)

/**
 * How far an index is built. Pages below [pagesDone] are in it, except [missing]: pages that could not be read
 * (a scanned page before the OCR models were in), tried again on the next build.
 */
data class IndexProgress(
    val pipelineVersion: Int,
    val pageCount: Int,
    val pagesDone: Int,
    val missing: Set<Int> = emptySet(),
) {
    val complete: Boolean get() = pagesDone >= pageCount
}

/** A page's sentences, or null when the page could not be read (it is then recorded as missing). */
data class IndexedPage(val page: Int, val units: List<ReadingUnit>?)

sealed interface SearchError {
    val message: String

    data class Storage(override val message: String) : SearchError
}

/** Blocking; called from one IO thread at a time. The SQLite version lives beside it; tests use an in-memory one. */
interface SearchIndex : Closeable {
    /** Null for a new (or unreadable) index. */
    fun progress(): IndexProgress?

    /** Empties the index for a new pipeline version or page count. */
    fun reset(pipelineVersion: Int, pageCount: Int): Outcome<Unit, SearchError>

    /**
     * Adds [pages] in one transaction: each page's old rows are replaced, a null page joins the missing ones, a read
     * page leaves them, and pagesDone moves past the highest page.
     */
    fun addPages(pages: List<IndexedPage>): Outcome<Unit, SearchError>

    /** Sentences whose terms match [match] (see [SearchTerms.match]), in reading order, at most [limit]. */
    fun search(match: String, limit: Int): Outcome<List<SearchHit>, SearchError>
}
