// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/storage/RoomBookStoreTest.kt
// Purpose: The Room library table on a real Android SQLite: order, unique keys as typed errors, round trip.

package io.github.skm0078.audora.playback.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookStore
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBookStoreTest {
    private lateinit var storage: AudoraStorage
    private lateinit var store: BookStore

    @Before
    fun setUp() {
        storage = AudoraStorage.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        store = storage.books
    }

    @After
    fun tearDown() = storage.close()

    private fun book(id: String, added: Long, opened: Long? = null, pages: Int? = null) = Book(
        id = BookId(id),
        documentUri = "content://test/document/$id",
        title = "Title $id",
        sizeBytes = 1_234L,
        fingerprint = "v1:$id",
        pageCount = pages,
        addedAtMillis = added,
        lastOpenedAtMillis = opened,
    )

    @Test
    fun roundTripKeepsEveryField() = runBlocking {
        val book = book("a", added = 10, opened = 20, pages = 574)

        assertEquals(Outcome.Success(Unit), store.insert(book))

        assertEquals(book, store.byId(book.id))
        assertEquals(book, store.byFingerprint(book.fingerprint))
        assertEquals(book, store.byDocumentUri(book.documentUri))
        assertNull(store.byId(BookId("missing")))
    }

    @Test
    fun listIsMostRecentlyOpenedOrAddedFirst() = runBlocking {
        store.insert(book("old", added = 10))
        store.insert(book("new", added = 30))
        store.insert(book("reopened", added = 5, opened = 40))

        assertEquals(listOf("reopened", "new", "old"), store.observeAll().first().map { it.id.value })
    }

    @Test
    fun duplicateFingerprintOrUriIsATypedError() = runBlocking {
        val original = book("a", added = 10)
        store.insert(original)

        val sameFingerprint = book("b", added = 11).copy(fingerprint = original.fingerprint)
        val sameUri = book("c", added = 12).copy(documentUri = original.documentUri)

        assertTrue(store.insert(sameFingerprint) is Outcome.Failure)
        assertTrue(store.insert(sameUri) is Outcome.Failure)
        assertEquals(1, store.observeAll().first().size)
    }

    @Test
    fun updateAndDelete() = runBlocking {
        val book = book("a", added = 10)
        store.insert(book)

        val moved = book.copy(documentUri = "content://test/document/moved", lastOpenedAtMillis = 99, pageCount = 3)
        assertEquals(Outcome.Success(Unit), store.update(moved))
        assertEquals(moved, store.byId(book.id))

        assertEquals(Outcome.Success(Unit), store.delete(book.id))
        assertNull(store.byId(book.id))
        assertTrue(store.update(book) is Outcome.Failure)
    }
}
