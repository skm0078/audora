// File: app/src/test/kotlin/io/github/skm0078/audora/ui/kids/KidsModeTest.kt
// Purpose: Kids mode (plan item 71): where it lets navigation go, and the slower speed it reads at.

package io.github.skm0078.audora.ui.kids

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsModeTest {
    private val book = BookId("b")

    @Test
    fun `books open in the player and grown-up screens land on the library`() {
        val cases = listOf(
            AppDestination.Library to AppDestination.Library,
            AppDestination.Player(book) to AppDestination.Player(book),
            AppDestination.Reader(book) to AppDestination.Player(book),
            AppDestination.Notes to AppDestination.Library,
            AppDestination.Settings to AppDestination.Library,
            AppDestination.Voices() to AppDestination.Library,
            AppDestination.Licences to AppDestination.Library,
            AppDestination.BatterySetup(fromSettings = true) to AppDestination.Library,
            AppDestination.BatterySetup(fromSettings = false) to AppDestination.Library,
        )
        cases.forEach { (asked, expected) -> assertEquals("$asked", expected, kidsDestination(asked)) }
    }

    @Test
    fun `reads at the kids speed unless the listener's own is slower`() {
        val cases = listOf(
            null to KIDS_SPEED,
            1f to KIDS_SPEED,
            2f to KIDS_SPEED,
            0.75f to 0.75f,
            KIDS_SPEED to KIDS_SPEED,
        )
        cases.forEach { (own, expected) -> assertEquals("$own", expected, kidsSpeed(own), 0f) }
    }
}
