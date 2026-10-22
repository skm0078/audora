// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SearchModel.kt
// Purpose: Search in the book (plan item 16): builds the book's index while the search screen is open, runs queries.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.search.BookSearch
import io.github.skm0078.audora.playback.search.IndexProgress
import io.github.skm0078.audora.playback.search.SearchError
import io.github.skm0078.audora.playback.search.SearchHit
import io.github.skm0078.audora.playback.search.SearchIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [progress] is null until the index is open; [failed] when it cannot be opened or written (logged). */
data class SearchState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val progress: IndexProgress? = null,
    val failed: Boolean = false,
) {
    val building: Boolean get() = progress?.complete != true && !failed
}

/** Where the search reads pages and keeps its index; the reader supplies both. */
class SearchSource(
    val pageCount: () -> Int?,
    val pages: suspend (Int) -> List<ReadingUnit>?,
    val openIndex: () -> Outcome<SearchIndex, SearchError>,
)

/**
 * Plain state holder, one per open book. The index is built only while the search screen is open ([open] to
 * [close]); closing it stops the build after the current page and the next [open] continues. Every index call runs
 * on [indexThread], one thread, so the build, the queries and the final close never overlap.
 */
class SearchModel(
    private val bookId: BookId,
    private val source: SearchSource,
    private val scope: CoroutineScope,
    private val indexThread: CoroutineDispatcher,
    private val pipelineVersion: Int,
) {
    private val mutableState = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutableState.asStateFlow()
    private var session: Job? = null
    private var queryJob: Job? = null
    private var search: BookSearch? = null

    fun open() {
        if (session?.isActive == true) return
        val pageCount = source.pageCount() ?: return
        mutableState.update { it.copy(failed = false) }
        session = scope.launch(indexThread) {
            val index = when (val opened = source.openIndex()) {
                is Outcome.Success -> opened.value

                is Outcome.Failure -> {
                    mutableState.update { it.copy(failed = true) }
                    return@launch
                }
            }
            try {
                val bookSearch = BookSearch(index, bookId, pipelineVersion)
                search = bookSearch
                val built = bookSearch.build(pageCount, source.pages) { progress ->
                    mutableState.update { it.copy(progress = progress, hits = hitsFor(it.query)) }
                }
                if (built is Outcome.Failure) mutableState.update { it.copy(failed = true) }
                awaitCancellation() // The index stays open for queries until the screen closes.
            } finally {
                search = null
                index.close()
            }
        }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        queryJob?.cancel()
        queryJob = scope.launch(indexThread) { mutableState.update { it.copy(hits = hitsFor(query)) } }
    }

    /** Stops the build (it continues on the next [open]); the query stays for when the screen comes back. */
    fun close() {
        queryJob?.cancel()
        session?.cancel()
        session = null
        AudoraLog.d(TAG, "Search closed, book=${bookId.logLabel}")
    }

    /** On [indexThread] only. */
    private fun hitsFor(query: String): List<SearchHit> {
        val current = search ?: return emptyList()
        return when (val found = current.search(query)) {
            is Outcome.Success -> found.value
            is Outcome.Failure -> emptyList()
        }
    }

    private companion object {
        const val TAG = "Search"
    }
}
