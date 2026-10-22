// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/search/BookSearchTest.kt
// Purpose: The index builds every page once, resumes after a stop, retries unreadable pages and resets on change.

package io.github.skm0078.audora.playback.search

import io.github.skm0078.audora.core.config.SearchConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.unit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BookSearchTest {
    private val index = MemorySearchIndex()
    private val config = SearchConfig(batchPages = 4)
    private val reads = mutableListOf<Int>()

    private fun search(pipeline: Int = 1) = BookSearch(index, BookId("book"), pipeline, config)

    private fun pages(unreadable: Set<Int> = emptySet()): suspend (Int) -> List<ReadingUnit>? = { page ->
        reads += page
        if (page in unreadable) null else listOf(unit(page, 0), unit(page, 1))
    }

    private fun hits(query: String) = (search().search(query) as Outcome.Success).value

    @Test
    fun `finds sentences in reading order once built`() = runTest {
        search().build(10, pages())
        assertEquals((0 until 10).toList(), reads)
        assertEquals(listOf(SearchHit(3, 10, "Sentence 1 of page 3.")), hits("sentence 1 of page 3"))
        assertEquals(20, hits("page").size)
        assertEquals(listOf(0, 0, 1, 1), hits("sente").take(4).map { it.page })
        assertTrue(hits("x").isEmpty())
    }

    @Test
    fun `a stopped build continues from the last saved batch`() = runTest {
        try {
            search().build(10, { page -> if (page == 6) throw CancellationException("closed") else pages()(page) })
            fail("not stopped")
        } catch (expected: CancellationException) {
            // The screen was closed.
        }
        reads.clear()
        search().build(10, pages())
        assertEquals(listOf(4, 5, 6, 7, 8, 9), reads)
        reads.clear()
        search().build(10, pages())
        assertTrue(reads.isEmpty())
    }

    @Test
    fun `unreadable pages are tried again next time`() = runTest {
        val first = search().build(6, pages(unreadable = setOf(2, 5)))
        assertEquals(setOf(2, 5), (first as Outcome.Success).value.missing)
        reads.clear()
        val second = search().build(6, pages())
        assertEquals(listOf(2, 5), reads)
        assertTrue((second as Outcome.Success).value.missing.isEmpty())
        assertEquals(listOf(2), hits("page 2").map { it.page }.distinct())
    }

    @Test
    fun `a new pipeline version or page count rebuilds`() = runTest {
        search().build(5, pages())
        reads.clear()
        search(pipeline = 2).build(5, pages())
        assertEquals((0 until 5).toList(), reads)
        reads.clear()
        search(pipeline = 2).build(7, pages())
        assertEquals((0 until 7).toList(), reads)
    }

    @Test
    fun `progress is reported after each batch`() = runTest {
        val seen = mutableListOf<Int>()
        search().build(10, pages()) { seen += it.pagesDone }
        assertEquals(listOf(0, 4, 8, 10), seen)
    }
}

/** The SQLite index's contract in memory: rows per page, prefix match on the last term. */
private class MemorySearchIndex : SearchIndex {
    private var progress: IndexProgress? = null
    private val rows = sortedMapOf<Int, List<ReadingUnit>>()

    override fun progress() = progress

    override fun reset(pipelineVersion: Int, pageCount: Int): Outcome<Unit, SearchError> {
        rows.clear()
        progress = IndexProgress(pipelineVersion, pageCount, 0)
        return Outcome.Success(Unit)
    }

    override fun addPages(pages: List<IndexedPage>): Outcome<Unit, SearchError> {
        val current = progress ?: return Outcome.Failure(SearchError.Storage("no index"))
        var missing = current.missing
        for (page in pages) {
            rows.remove(page.page)
            if (page.units == null) {
                missing = missing + page.page
            } else {
                rows[page.page] = page.units
                missing = missing - page.page
            }
        }
        progress = current.copy(pagesDone = maxOf(current.pagesDone, pages.maxOf { it.page } + 1), missing = missing)
        return Outcome.Success(Unit)
    }

    override fun search(match: String, limit: Int): Outcome<List<SearchHit>, SearchError> {
        val wanted = match.split(' ')
        val hits = rows.values.flatten().filter { unit ->
            val terms = SearchTerms.of(unit.text)
            wanted.all { term ->
                if (term.endsWith('*')) terms.any { it.startsWith(term.dropLast(1)) } else term in terms
            }
        }
        return Outcome.Success(hits.take(limit).map { SearchHit(it.page, it.charStart, it.text) })
    }

    override fun close() = Unit
}
