// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/storage/RoomCollectionStoreTest.kt
// Purpose: Collections on a real Android SQLite (ADR-069): make, rename, delete, membership, what removing either
//          side does, and a 1,000-book library read and searched in one go.

package io.github.skm0078.audora.playback.storage

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CollectionStore
import io.github.skm0078.audora.core.library.LibraryIndex
import io.github.skm0078.audora.core.library.LibraryNarrow
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.library.LibrarySort
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCollectionStoreTest {
    private lateinit var storage: AudoraStorage
    private lateinit var store: CollectionStore

    @Before
    fun setUp() {
        storage = AudoraStorage.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        store = storage.collections
    }

    @After
    fun tearDown() = storage.close()

    private fun book(n: Int) = Book(
        id = BookId("book-$n"),
        documentUri = "content://test/document/$n",
        title = "Title $n",
        sizeBytes = 1_234L,
        fingerprint = "v1:$n",
        pageCount = 10,
        addedAtMillis = n.toLong(),
        lastOpenedAtMillis = null,
        author = "Author ${n % AUTHORS}",
    )

    @Test
    fun collectionsAreListedByNameAndRenamed() = runBlocking {
        store.create(BookCollection("b", "bedtime", 2))
        store.create(BookCollection("a", "For school", 1))
        assertEquals(listOf("bedtime", "For school"), store.observe().first().map { it.name })

        assertEquals(Outcome.Success(Unit), store.rename("b", "Stories"))
        assertEquals(listOf("For school", "Stories"), store.observe().first().map { it.name })
        assertTrue(store.rename("missing", "x") is Outcome.Failure)
    }

    @Test
    fun aBookCanBeInSeveralAndAddingTwiceKeepsOne() = runBlocking {
        storage.books.insert(book(1))
        store.create(BookCollection("a", "A", 1))
        store.create(BookCollection("b", "B", 1))
        store.setMember(BookId("book-1"), "a", member = true, atMillis = 5)
        store.setMember(BookId("book-1"), "a", member = true, atMillis = 6)
        store.setMember(BookId("book-1"), "b", member = true, atMillis = 7)
        assertEquals(mapOf(BookId("book-1") to setOf("a", "b")), store.observeMembers().first())

        store.setMember(BookId("book-1"), "a", member = false, atMillis = 8)
        assertEquals(mapOf(BookId("book-1") to setOf("b")), store.observeMembers().first())
    }

    @Test
    fun deletingACollectionKeepsTheBookAndRemovingABookLeavesTheCollection() = runBlocking {
        storage.books.insert(book(1))
        storage.books.insert(book(2))
        store.create(BookCollection("a", "A", 1))
        store.setMember(BookId("book-1"), "a", member = true, atMillis = 1)
        store.setMember(BookId("book-2"), "a", member = true, atMillis = 1)

        storage.books.delete(BookId("book-1"))
        assertEquals(mapOf(BookId("book-2") to setOf("a")), store.observeMembers().first())

        store.delete("a")
        assertEquals(emptyMap<BookId, Set<String>>(), store.observeMembers().first())
        assertEquals(listOf("book-2"), storage.books.observeAll().first().map { it.id.value })
    }

    /** What the library does on a phone with a big library: every book and every link read once, then searched. */
    @Test
    fun aThousandBooksAreReadAndSearchedWithCollectionsInOneGo() = runBlocking {
        (0 until LARGE_LIBRARY).forEach { storage.books.insert(book(it)) }
        store.create(BookCollection("even", "Even", 1))
        (0 until LARGE_LIBRARY step 2).forEach { store.setMember(BookId("book-$it"), "even", true, 1) }

        val started = SystemClock.elapsedRealtime()
        val books = storage.books.observeAll().first()
        val members = store.observeMembers().first()
        val index = LibraryIndex.of(books)
        val query = LibraryQuery("author 3", LibrarySort.AUTHOR, narrow = LibraryNarrow(collection = "even"))
        val found = query.apply(books, { it }, { null }, index, members)
        val tookMs = SystemClock.elapsedRealtime() - started

        assertEquals(LARGE_LIBRARY, books.size)
        assertEquals(LARGE_LIBRARY / 2, members.size)
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { "even" in members.getValue(it.id) })
        // Generous for a slow emulator; a regression to one query per book would blow it.
        assertTrue("took $tookMs ms", tookMs < BUDGET_MS)
    }

    private companion object {
        const val LARGE_LIBRARY = 1_000
        const val AUTHORS = 40
        const val BUDGET_MS = 3_000L
    }
}
