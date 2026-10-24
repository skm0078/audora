// File: app/src/test/kotlin/io/github/skm0078/audora/ui/AppDestinationTest.kt
// Purpose: Every screen survives its saved route, back goes where expected, and tabs belong to the right screens.

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    private val book = BookId("0b5e")
    private val all = listOf(
        AppDestination.Library,
        AppDestination.Notes,
        AppDestination.Settings,
        AppDestination.Voices(),
        AppDestination.Voices(fromStorage = true),
        AppDestination.Licences,
        AppDestination.Storage,
        AppDestination.Jobs,
        AppDestination.Reader(book),
        AppDestination.Reader(book, AppTab.NOTES),
        AppDestination.Reader(book, AppTab.SETTINGS, viaPlayer = true),
        AppDestination.Player(book),
        AppDestination.Player(book, AppTab.NOTES),
        AppDestination.BatterySetup(fromSettings = false),
        AppDestination.BatterySetup(fromSettings = true),
    )

    @Test
    fun everyRouteRoundTrips() {
        all.forEach { assertEquals(it, AppDestination.fromRoute(it.toRoute())) }
    }

    @Test
    fun unknownRoutesFallBackToTheLibrary() {
        listOf(null, "", "player/", "book/", "nowhere").forEach {
            assertEquals(AppDestination.Library, AppDestination.fromRoute(it))
        }
    }

    @Test
    fun tabsAndParents() {
        assertEquals(AppTab.entries.map { it.destination }, all.filter { it.tab != null })
        AppTab.entries.forEach { assertEquals(it, it.destination.tab) }
        assertNull(AppDestination.Player(book).tab)
        assertEquals(AppDestination.Library, AppDestination.Player(book).parent)
        assertEquals(AppDestination.Library, AppDestination.Notes.parent)
    }

    @Test
    fun jobsGoBackToTheLibraryWithNoTab() {
        assertEquals(AppDestination.Library, AppDestination.Jobs.parent)
        assertNull(AppDestination.Jobs.tab)
    }

    @Test
    fun voicesOpenedFromStorageGoBackToStorage() {
        assertEquals(AppDestination.Storage, AppDestination.Voices(fromStorage = true).parent)
        assertEquals(AppDestination.Settings, AppDestination.Voices().parent)
    }

    @Test
    fun backReturnsWhereTheBookWasOpened() {
        assertEquals(AppDestination.Notes, AppDestination.Reader(book, AppTab.NOTES).parent)
        assertEquals(AppDestination.Settings, AppDestination.Player(book, AppTab.SETTINGS).parent)
        assertEquals(
            AppDestination.Player(book, AppTab.NOTES),
            AppDestination.Reader(book, AppTab.NOTES, viaPlayer = true).parent,
        )
    }

    @Test
    fun olderBookRoutesStillOpenFromTheLibrary() {
        assertEquals(AppDestination.Reader(book), AppDestination.fromRoute("book/0b5e"))
        assertEquals(AppDestination.Player(book), AppDestination.fromRoute("player/0b5e"))
        assertEquals("book/0b5e", AppDestination.Reader(book).toRoute())
        assertEquals(AppDestination.Library, AppDestination.fromRoute("book/?from=notes"))
    }

    @Test
    fun oneScreenForTheTabsAndOneForEachBook() {
        assertEquals(1, AppTab.entries.map { it.destination.screen }.distinct().size)
        assertEquals(
            AppDestination.Player(book, AppTab.NOTES).screen,
            AppDestination.Reader(book, AppTab.LIBRARY, viaPlayer = true).screen,
        )
        assertNotEquals(AppDestination.Reader(book).screen, AppDestination.Reader(BookId("other")).screen)
        val pages = all.filter { it.tab == null && it !is AppDestination.Reader && it !is AppDestination.Player }
        val screens = pages.map { it.screen }
        assertEquals(screens.distinct(), screens)
        assertNotEquals(AppDestination.Library.screen, AppDestination.Voices().screen)
    }
}
