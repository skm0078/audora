// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/search/BookSearch.kt
// Purpose: Builds a book's search index page by page, resuming where it stopped, and answers queries (item 16).

package io.github.skm0078.audora.playback.search

import io.github.skm0078.audora.core.config.SearchConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The index is a cache of the pipeline's output, so it is rebuilt from scratch when [pipelineVersion] or the page
 * count changes. Building reads every page once through [pages] (the reader's own document, so scanned pages come
 * from the OCR cache when they were read before); a closed screen stops it and the next search continues from the
 * last saved batch. Queries see the pages built so far.
 *
 * Search terms and sentences are the listener's book: never logged, only counts.
 */
class BookSearch(
    private val index: SearchIndex,
    private val book: BookId,
    private val pipelineVersion: Int,
    private val config: SearchConfig = SearchConfig(),
) {
    private val logContext = "book=${book.logLabel}"

    /**
     * Brings the index up to date with a book of [pageCount] pages. [pages] returns a page's sentences, or null when
     * it cannot be read now; [onProgress] gets the index's progress after each batch. Cancellable between pages.
     */
    suspend fun build(
        pageCount: Int,
        pages: suspend (Int) -> List<ReadingUnit>?,
        onProgress: (IndexProgress) -> Unit = {},
    ): Outcome<IndexProgress, SearchError> {
        val start = startFrom(pageCount) ?: return Outcome.Failure(SearchError.Storage("index unreadable"))
        onProgress(start)
        val todo = start.missing.sorted() + (start.pagesDone until pageCount)
        return if (todo.isEmpty()) Outcome.Success(start) else fill(todo, start, pages, onProgress)
    }

    private suspend fun fill(
        todo: List<Int>,
        start: IndexProgress,
        pages: suspend (Int) -> List<ReadingUnit>?,
        onProgress: (IndexProgress) -> Unit,
    ): Outcome<IndexProgress, SearchError> {
        AudoraLog.i(TAG, "Search index building, $logContext pages=${todo.size} of ${start.pageCount}")
        var progress = start
        for (batch in todo.chunked(config.batchPages)) {
            val read = batch.map { page ->
                coroutineContext.ensureActive()
                IndexedPage(page, pages(page))
            }
            when (val added = index.addPages(read)) {
                is Outcome.Failure -> {
                    AudoraLog.e(TAG, "Search index not saved, $logContext pages=${batch.first()}..${batch.last()}")
                    return added
                }

                is Outcome.Success -> progress = index.progress() ?: progress
            }
            onProgress(progress)
        }
        AudoraLog.i(TAG, "Search index built, $logContext pages=${start.pageCount} missing=${progress.missing.size}")
        return Outcome.Success(progress)
    }

    /** Sentences matching what the listener typed; empty when there is too little to search for. */
    fun search(query: String): Outcome<List<SearchHit>, SearchError> {
        val match = SearchTerms.match(query, config) ?: return Outcome.Success(emptyList())
        return index.search(match, config.maxHits)
    }

    private fun startFrom(pageCount: Int): IndexProgress? {
        val current = index.progress()
        if (current != null && current.pipelineVersion == pipelineVersion && current.pageCount == pageCount) {
            return current
        }
        AudoraLog.i(TAG, "Search index reset, $logContext pipeline=$pipelineVersion pages=$pageCount")
        return when (index.reset(pipelineVersion, pageCount)) {
            is Outcome.Success -> index.progress()
            is Outcome.Failure -> null
        }
    }

    private companion object {
        const val TAG = "Search"
    }
}
