// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/storage/RoomResumeAnchorStoreTest.kt
// Purpose: Resume anchors on a real Android SQLite: round trip, replace, deleted with their book, typed errors.

package io.github.skm0078.audora.playback.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomResumeAnchorStoreTest {
    private lateinit var storage: AudoraStorage
    private val book = Book(BookId("book-a"), "content://test/a", "A", 1L, "v1:a", 10, 1L, null)

    @Before
    fun setUp() {
        storage = AudoraStorage.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { storage.books.insert(book) }
    }

    @After
    fun tearDown() = storage.close()

    private fun anchor(page: Int, charStart: Int) = ResumeAnchor(
        bookId = book.id,
        page = page,
        charStart = charStart,
        fingerprint = Long.MIN_VALUE + page,
        tailFingerprint = Long.MAX_VALUE - charStart,
        pipelineVersion = 3,
        savedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun roundTripKeepsEveryField() = runBlocking {
        val saved = anchor(page = 41, charStart = 1_234)

        assertEquals(Outcome.Success(Unit), storage.resumeAnchors.put(saved))

        assertEquals(saved, storage.resumeAnchors.get(book.id))
        assertNull(storage.resumeAnchors.get(BookId("never-read")))
    }

    @Test
    fun aNewSaveReplacesTheOld() = runBlocking {
        storage.resumeAnchors.put(anchor(page = 1, charStart = 0))
        storage.resumeAnchors.put(anchor(page = 2, charStart = 77))

        assertEquals(anchor(page = 2, charStart = 77), storage.resumeAnchors.get(book.id))
    }

    @Test
    fun removingTheBookRemovesItsAnchor() = runBlocking {
        storage.resumeAnchors.put(anchor(page = 5, charStart = 9))

        storage.books.delete(book.id)

        assertNull(storage.resumeAnchors.get(book.id))
    }

    @Test
    fun anAnchorForABookNotInTheLibraryIsATypedError() = runBlocking {
        val orphan = anchor(page = 0, charStart = 0).copy(bookId = BookId("removed"))

        val result = storage.resumeAnchors.put(orphan)

        assertTrue("$result", result is Outcome.Failure)
        assertNull(storage.resumeAnchors.get(orphan.bookId))
    }
}
