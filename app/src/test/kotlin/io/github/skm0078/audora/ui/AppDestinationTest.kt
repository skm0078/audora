// File: app/src/test/kotlin/io/github/skm0078/audora/ui/AppDestinationTest.kt
// Purpose: Every screen survives its saved route, back goes where expected, and tabs belong to the right screens.

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    private val book = BookId("0b5e")
    private val all = listOf(
        AppDestination.Library,
        AppDestination.Notes,
        AppDestination.Settings,
        AppDestination.Voices,
        AppDestination.Licences,
        AppDestination.Reader(book),
        AppDestination.Player(book),
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
}
