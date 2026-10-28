// File: app/src/test/kotlin/io/github/skm0078/audora/ui/shell/PlacesFormTest.kt
// Purpose: The four places take the form the window's width class asks for, at exactly Material's breakpoints.

package io.github.skm0078.audora.ui.shell

import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.ui.theme.WindowWidths
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacesFormTest {
    @Test
    fun aPhoneHasTheBarASmallTabletTheRailAndABigTabletTheSidebar() {
        assertEquals(PlacesForm.BAR, placesForm(showPlaces = true, 360.dp))
        assertEquals(PlacesForm.BAR, placesForm(showPlaces = true, WindowWidths.MEDIUM - 1.dp))
        assertEquals(PlacesForm.RAIL, placesForm(showPlaces = true, WindowWidths.MEDIUM))
        assertEquals(PlacesForm.RAIL, placesForm(showPlaces = true, WindowWidths.EXPANDED - 1.dp))
        assertEquals(PlacesForm.SIDEBAR, placesForm(showPlaces = true, WindowWidths.EXPANDED))
        assertEquals(PlacesForm.SIDEBAR, placesForm(showPlaces = true, 1280.dp))
    }

    @Test
    fun kidsModeHasNoPlacesAtAnyWidth() {
        listOf(360.dp, 700.dp, 1280.dp).forEach { assertEquals(PlacesForm.NONE, placesForm(showPlaces = false, it)) }
    }
}
