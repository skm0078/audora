// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryLoadTest.kt
// Purpose: A big library (ADR 2026-10-28-performance-budget): 1,000 and 5,000 books through the real Library and
//          LibraryController stay inside LibraryLoadBudget for the first list, a sort or shelf change, a typed search
//          and the file check, grow no worse than linearly, and narrow only once typing pauses. The numbers go to
//          build/load-test/library-load.md, which CI shows on the run's summary page.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.config.LibraryLoadBudget
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookFingerprinter
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.DocumentError
import io.github.skm0078.audora.core.library.DocumentInfo
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.LibraryFacets
import io.github.skm0078.audora.core.library.LibraryFilter
import io.github.skm0078.audora.core.library.LibraryIndex
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.library.LibrarySort
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.library.SyntheticLibrary
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLoadTest {
    private val budget = LibraryLoadBudget()

    @Test
    fun `a big library lists, sorts, searches and checks its files inside the budget`() {
        val small = fastest(budget.smallLibraryBooks)
        val large = fastest(budget.largeLibraryBooks)
        report(small, large)

        val limits = mapOf(
            Step.INDEX to budget.firstListMs,
            Step.FIRST_LIST to budget.firstListMs,
            Step.QUERY_CHANGE to budget.queryChangeMs,
            Step.SEARCH to budget.searchMs,
            Step.AVAILABILITY to budget.availabilityMs,
        )
        limits.forEach { (step, limitMs) ->
            val tookMs = large.getValue(step)
            assertTrue(
                "${step.label} took $tookMs ms for ${budget.largeLibraryBooks} books (budget $limitMs ms)",
                tookMs <= limitMs,
            )
            val smallMs = small.getValue(step).coerceAtLeast(1)
            val growth = tookMs.toDouble() / smallMs
            assertTrue(
                "${step.label} grew ${"%.1f".format(growth)}x from ${budget.smallLibraryBooks} to " +
                    "${budget.largeLibraryBooks} books (budget ${budget.maxGrowth}x): work that compares every book " +
                    "with every other",
                tookMs < budget.growthFloorMs || growth <= budget.maxGrowth,
            )
        }
    }

    @Test
    fun `typing into search narrows the big list once, when typing pauses`() = runTest(UnconfinedTestDispatcher()) {
        val books = SyntheticLibrary.books(budget.largeLibraryBooks)
        val controller = controllerFor(books)
        val target = books.last().title

        target.indices.forEach { end -> controller.setQuery(LibraryQuery(text = target.take(end + 1))) }

        assertEquals("No narrowing while keys keep coming", books.size, controller.loaded().items.size)
        assertEquals("What search finds waits with the books", "", controller.loaded().narrowedFor)
        typingPauses()
        assertEquals(listOf(books.last().id), controller.loaded().items.map { it.book.id })
        assertEquals(target, controller.loaded().narrowedFor)
    }

    /** The best of a few runs per step: the first warms the JIT, and a slow neighbour on CI only ever adds time. */
    private fun fastest(size: Int): Map<Step, Long> {
        val runs = List(RUNS) { timings(size) }
        return Step.entries.associateWith { step -> runs.minOf { it.getValue(step) } }
    }

    private fun timings(size: Int): Map<Step, Long> {
        val took = mutableMapOf<Step, Long>()
        runTest(UnconfinedTestDispatcher()) {
            val books = SyntheticLibrary.books(size)
            took[Step.INDEX] = timed { LibraryFacets.of(books, LibraryIndex.of(books)) }
            lateinit var controller: LibraryController
            took[Step.FIRST_LIST] = timed { controller = controllerFor(books) }
            assertEquals(size, controller.loaded().items.size)

            took[Step.QUERY_CHANGE] = timed {
                controller.setQuery(LibraryQuery(sort = LibrarySort.TITLE, filter = LibraryFilter.IN_PROGRESS))
            }
            assertTrue("Some books are being read", controller.loaded().items.isNotEmpty())

            // Only the text changes, as when the listener types: the list narrows once typing pauses.
            val target = SyntheticLibrary.read(books).last()
            controller.setQuery(controller.loaded().query.copy(text = target.title))
            took[Step.SEARCH] = timed { typingPauses() }
            assertEquals(listOf(target.id), controller.loaded().items.map { it.book.id })

            val library = libraryOf(books)
            took[Step.AVAILABILITY] = timed { library.availability(books) }
        }
        return took
    }

    private fun TestScope.typingPauses() {
        advanceTimeBy(TYPING_PAUSE_MS)
        runCurrent()
    }

    private fun TestScope.libraryOf(books: List<Book>) = Library(
        MemoryBooks(books),
        FreeDocuments,
        BookFingerprinter(),
        clock = { NOW },
        io = UnconfinedTestDispatcher(testScheduler),
    )

    private fun TestScope.controllerFor(books: List<Book>): LibraryController = LibraryController(
        libraryOf(books),
        backgroundScope,
        anchors = flowOf(SyntheticLibrary.anchors(books)),
        collections = flowOf(SyntheticLibrary.collections()),
        members = flowOf(SyntheticLibrary.members(books)),
    ).apply { start() }

    private fun LibraryController.loaded() = listState as LibraryListState.Loaded

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / NANOS_PER_MS
    }

    private fun report(small: Map<Step, Long>, large: Map<Step, Long>) {
        val rows = Step.entries.joinToString("\n") { step ->
            "| ${step.label} | ${small.getValue(step)} | ${large.getValue(step)} |"
        }
        val table = "### Library load test (JVM, best of $RUNS)\n\n" +
            "| Step | ${budget.smallLibraryBooks} books (ms) | ${budget.largeLibraryBooks} books (ms) |\n" +
            "|---|---|---|\n$rows\n"
        File(REPORT).apply { parentFile?.mkdirs() }.writeText(table)
    }

    private enum class Step(val label: String) {
        INDEX("Search index and filter lists"),
        FIRST_LIST("First list (index, filters, progress, file check)"),
        QUERY_CHANGE("Sort by title and the Reading shelf"),
        SEARCH("Typed search, after the pause"),
        AVAILABILITY("File check, free checks"),
    }

    private class MemoryBooks(books: List<Book>) : BookStore {
        private val books = MutableStateFlow(books.associateBy { it.id })

        override fun observeAll(): Flow<List<Book>> = books.map { it.values.toList() }

        override suspend fun byId(id: BookId): Book? = books.value[id]

        override suspend fun byFingerprint(fingerprint: String): Book? =
            books.value.values.find { it.fingerprint == fingerprint }

        override suspend fun byDocumentUri(documentUri: String): Book? =
            books.value.values.find { it.documentUri == documentUri }

        override suspend fun insert(book: Book): Outcome<Unit, StorageError> = update(book)

        override suspend fun update(book: Book): Outcome<Unit, StorageError> {
            books.value += book.id to book
            return Outcome.Success(Unit)
        }

        override suspend fun delete(id: BookId): Outcome<Unit, StorageError> {
            books.value -= id
            return Outcome.Success(Unit)
        }
    }

    /** Every file is there and checking costs nothing, so a time measured is the library's own work. */
    private object FreeDocuments : DocumentAccess {
        override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = Outcome.Success(Unit)

        override fun releaseAccess(documentUri: String) = Unit

        override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))

        override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> =
            Outcome.Failure(DocumentError.NotFound("not used"))

        override fun availability(documentUri: String) = BookAvailability.AVAILABLE

        override fun pruneUnused(inUse: Set<String>) = Unit
    }

    private companion object {
        const val RUNS = 3
        const val NANOS_PER_MS = 1_000_000L
        const val NOW = 1_800_000_000_000L

        /** Longer than the controller's wait for typing to pause, so the search has run when it ends. */
        const val TYPING_PAUSE_MS = 1_000L
        const val REPORT = "build/load-test/library-load.md"
    }
}
