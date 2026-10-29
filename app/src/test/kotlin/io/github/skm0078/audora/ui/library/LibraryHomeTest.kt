// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryHomeTest.kt
// Purpose: The home parts follow the listener's order and step aside for a search; the line of the day only names a
//          book still in the library; a cover tint always leaves the label readable (ADR 2026-10-28-make-it-yours).

package io.github.skm0078.audora.ui.library

import androidx.compose.ui.graphics.Color
import io.github.skm0078.audora.core.home.HomeLayout
import io.github.skm0078.audora.core.home.HomeSection
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHomeTest {
    private fun item(id: String, addedAt: Long = 0) = LibraryItem(
        book = Book(BookId(id), "content://$id", id.uppercase(), 1L, "fp-$id", 10, addedAt, null),
        availability = BookAvailability.AVAILABLE,
        progress = BookProgress(page = 2, pageCount = 10, savedAtMillis = 0),
    )

    private val a = item("a", addedAt = 1)
    private val b = item("b", addedAt = 3)
    private val c = item("c", addedAt = 2)
    private val list = LibraryListState.Loaded(listOf(a, b, c), latest = b)
    private val line = DayLine("A line.", a.book, 1, anchor("a"))

    private fun keys(home: LibraryHome, shown: LibraryListState.Loaded = list) =
        homeParts(shown, home, CoverSource.None, {}, {}).map { it.key }

    @Test
    fun `the parts follow the listener's order and leave out what is off`() {
        val layout = HomeLayout()
            .toggled(HomeSection.RECENTLY_ADDED)
            .moved(HomeSection.RECENTLY_ADDED, up = true)
            .moved(HomeSection.RECENTLY_ADDED, up = true)
            .toggled(HomeSection.LINE_OF_THE_DAY)
        assertEquals(listOf(RECENT_KEY, CONTINUE_KEY, FROM_LISTENLOCK_KEY), keys(LibraryHome(layout, line = line)))
    }

    @Test
    fun `a part with nothing to show is left out`() {
        assertEquals(listOf(CONTINUE_KEY, FROM_LISTENLOCK_KEY), keys(LibraryHome(HomeLayout(), line = null)))
        assertEquals(
            listOf(CONTINUE_KEY, LINE_KEY, FROM_LISTENLOCK_KEY),
            keys(LibraryHome(HomeLayout(), line = line)),
        )
    }

    @Test
    fun `From ListenLock is labelled apart from the books and can be turned off`() {
        assertTrue(FROM_LISTENLOCK_KEY in keys(LibraryHome(HomeLayout())))
        val off = HomeLayout().toggled(HomeSection.FROM_LISTENLOCK)
        assertFalse(FROM_LISTENLOCK_KEY in keys(LibraryHome(off)))
        assertTrue(AppDocument.entries.all { it.action.inMenu })
    }

    @Test
    fun `a search or a filter puts the books first`() {
        val searched = list.copy(query = LibraryQuery(text = "moby"))
        assertTrue(keys(LibraryHome(HomeLayout(), line = line), searched).isEmpty())
    }

    @Test
    fun `nothing shows until the choices are read`() {
        assertTrue(keys(LibraryHome(layout = null, line = line)).isEmpty())
        assertTrue(showsShelfChips(LibraryHome(layout = null)))
    }

    @Test
    fun `the chips can be turned off`() {
        assertFalse(showsShelfChips(LibraryHome(HomeLayout().toggled(HomeSection.SHELF_CHIPS))))
    }

    @Test
    fun `recently added is newest first`() {
        assertEquals(listOf(b, c, a), recentlyAdded(list))
    }

    @Test
    fun `the line of the day only names a book still in the library`() {
        val kept = highlight(1, "a", "Kept line.")
        val gone = highlight(2, "gone", "Removed book's line.")
        val picked = dayLine(listOf(kept, gone), listOf(a.book), epochDay = 0)
        assertEquals("Kept line.", picked?.quote)
        assertEquals(a.book, picked?.book)
        assertEquals(kept.anchor.page + 1, picked?.page)
        assertNull(dayLine(listOf(gone), listOf(a.book), epochDay = 0))
    }

    @Test
    fun `a tint keeps the label readable on pale and dark covers in both themes`() {
        val themes = listOf(Color(0xFFE6EFE6) to Color(0xFF1A1C1A), Color(0xFF2A322B) to Color(0xFFE2E3DE))
        val covers = listOf(Color.White, Color.Black, Color(0xFFFFEB3B), Color(0xFF0D47A1), Color(0xFF808080))
        for ((surface, onSurface) in themes) {
            for (cover in covers) {
                val tint = coverTint(cover, surface, onSurface)
                assertTrue("cover $cover on $surface", contrast(tint.accent, tint.container) >= MIN_READABLE)
            }
        }
    }

    @Test
    fun `contrast is the WCAG ratio`() {
        assertEquals(21f, contrast(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrast(Color.White, Color.White), 0.01f)
    }

    private fun highlight(id: Long, book: String, quote: String) =
        Highlight(id = id, anchor = anchor(book), endPage = 3, endChar = 5, quote = quote)

    private fun anchor(book: String) = ResumeAnchor(
        BookId(book),
        page = 3,
        charStart = 0,
        fingerprint = 0,
        tailFingerprint = 0,
        pipelineVersion = 1,
        savedAtMillis = 0,
    )

    private companion object {
        const val MIN_READABLE = 4.5f
    }
}
