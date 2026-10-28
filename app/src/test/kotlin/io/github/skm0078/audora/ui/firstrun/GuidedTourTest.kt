// File: app/src/test/kotlin/io/github/skm0078/audora/ui/firstrun/GuidedTourTest.kt
// Purpose: The 1-minute tour (ADR 2026-10-28-home-and-tour) is offered once, out of kids mode, to a library with a
//          book; walks its five stops in order; and any answer marks it offered. The library's greeting follows the
//          hour.

package io.github.skm0078.audora.ui.firstrun

import androidx.compose.runtime.mutableIntStateOf
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.greetingFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTourTest {
    private val book = LibraryItem(Book(BookId("a"), "content://a", "a", 1L, "fp-a", 10, 0L, null), null)
    private var offered = 0
    private val tour = GuidedTour(mutableIntStateOf(GuidedTour.HIDDEN)) { offered++ }

    @Test
    fun `offered once, out of kids mode, to a loaded library with a book`() {
        val withBook = LibraryListState.Loaded(listOf(book))
        assertTrue(shouldOfferTour(offered = false, kids = false, library = withBook))
        assertFalse(shouldOfferTour(offered = true, kids = false, library = withBook))
        assertFalse(shouldOfferTour(offered = false, kids = true, library = withBook))
        assertFalse(shouldOfferTour(offered = false, kids = false, library = LibraryListState.Loaded(emptyList())))
        assertFalse(shouldOfferTour(offered = false, kids = false, library = LibraryListState.Loading))
        assertFalse(shouldOfferTour(offered = false, kids = false, library = LibraryListState.Failed))
    }

    @Test
    fun `starting walks the five stops in order and Next on the last closes it`() {
        tour.offer()
        assertTrue(tour.offering)
        tour.start()
        val seen = mutableListOf<TourStop>()
        while (tour.stop != null) {
            seen += requireNotNull(tour.stop)
            tour.next()
        }
        assertEquals(TourStop.entries, seen)
        assertFalse(tour.offering)
        assertEquals(1, offered)
    }

    @Test
    fun `Back steps to the stop before and closes from the first`() {
        tour.start()
        tour.next()
        tour.back()
        assertEquals(TourStop.LISTEN, tour.stop)
        tour.back()
        assertNull(tour.stop)
    }

    @Test
    fun `Maybe later marks it offered and a closed tour is not offered again by itself`() {
        tour.offer()
        tour.close()
        assertFalse(tour.offering)
        assertNull(tour.stop)
        assertEquals(1, offered)
        tour.start()
        tour.offer()
        assertEquals("an offer never interrupts a stop", TourStop.LISTEN, tour.stop)
    }

    @Test
    fun `each stop points at its own target`() {
        assertEquals(
            listOf(TourTarget.PLAY, TourTarget.BOOK, TourTarget.NOTES, TourTarget.MADE, TourTarget.YOU),
            TourStop.entries.map { it.target },
        )
    }

    @Test
    fun `the greeting follows the hour`() {
        assertEquals(R.string.library_greeting_evening, greetingFor(4))
        assertEquals(R.string.library_greeting_morning, greetingFor(5))
        assertEquals(R.string.library_greeting_morning, greetingFor(11))
        assertEquals(R.string.library_greeting_afternoon, greetingFor(12))
        assertEquals(R.string.library_greeting_afternoon, greetingFor(16))
        assertEquals(R.string.library_greeting_evening, greetingFor(17))
        assertEquals(R.string.library_greeting_evening, greetingFor(23))
    }
}
