// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/StudioStoreTest.kt
// Purpose: The studio store keeps one folder per book and signature, prunes listened pages and fits its budget.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.unit
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioStoreTest {
    private val root: File = Files.createTempDirectory("studio").toFile()
    private val store = StudioStore(root)
    private val codec = LosslessPcm(BLOCK)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun key(book: String, rate: Float = 1f) = StudioKey(
        BookId(book),
        StudioSignature(1, 0L, SpeechStyle(rate = rate), emptyMap(), fixes = ""),
    )

    private fun write(key: StudioKey, page: Int, samples: Int = 100): Long {
        val written = store.write(
            key,
            StudioPage(page, listOf(StudioClip.of(unit(page, 0), StudioVoiced(pcm(samples)), codec))),
        )
        return (written as Outcome.Success).value
    }

    @Test
    fun `finds a page only under the same signature`() {
        write(key("a"), 0)
        assertNotNull(store.page(key("a"), 0))
        assertNull(store.page(key("a", rate = 1.2f), 0))
        assertNull(store.page(key("b"), 0))
        assertNull(store.page(key("a"), 1))
    }

    @Test
    fun `pruning drops listened pages and old signatures`() {
        val old = key("a", rate = 1.2f)
        write(old, 0)
        (0..4).forEach { write(key("a"), it) }
        store.prune(key("a"), keepFromPage = 3)
        assertNull(store.page(key("a"), 2))
        assertNotNull(store.page(key("a"), 3))
        assertNull(store.page(old, 0))
    }

    @Test
    fun `a damaged page is deleted and reads as missing`() {
        write(key("a"), 0)
        val file = root.walkTopDown().first { it.name == "p0.studio" }
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.page(key("a"), 0))
        assertFalse(file.exists())
    }

    @Test
    fun `fitting the budget deletes other books first, never the one being rendered`() {
        write(key("old"), 0, samples = 5_000)
        Thread.sleep(SLEEP_MS) // Distinct modification times.
        write(key("keep"), 0, samples = 5_000)
        val one = store.totalBytes() / 2
        assertTrue(store.fitWithin(one + 64, keep = BookId("keep")))
        assertNull(store.page(key("old"), 0))
        assertNotNull(store.page(key("keep"), 0))
        assertFalse(store.fitWithin(1, keep = BookId("keep")))
    }

    @Test
    fun `books removed from the library lose their audio`() {
        write(key("a"), 0)
        write(key("b"), 0)
        store.dropBooksNotIn(setOf(BookId("b")))
        assertNull(store.page(key("a"), 0))
        assertNotNull(store.page(key("b"), 0))
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test
    fun `a book's summary counts its newest pages, their speech and its bytes`() {
        val old = key("a", rate = 1.2f)
        write(old, 0)
        Thread.sleep(SLEEP_MS) // The current signature is the one written last.
        (3..5).forEach { write(key("a"), it, samples = 2_000) }
        val audio = store.bookAudio(BookId("a"))
        assertNotNull(audio)
        assertEquals(3, audio!!.firstPage)
        assertEquals(5, audio.lastPage)
        assertEquals(3, audio.pages)
        assertEquals(3 * StudioClip.of(unit(0, 0), StudioVoiced(pcm(2_000)), codec).durationMs, audio.durationMs)
        assertTrue(audio.bytes > 0)
        assertEquals(listOf(3, 4, 5), store.readyPages(BookId("a")).map { it.page }.toList())
        assertNull(store.bookAudio(BookId("none")))
    }

    @Test
    fun `deleting a book's audio leaves the other books'`() {
        write(key("a"), 0)
        write(key("b"), 0)
        assertTrue(store.deleteBook(BookId("a")))
        assertNull(store.bookAudio(BookId("a")))
        assertNotNull(store.page(key("b"), 0))
        assertTrue(store.deleteBook(BookId("a"))) // Nothing left is not a failure.
    }

    private companion object {
        const val BLOCK = 256
        const val SLEEP_MS = 20L
    }
}
