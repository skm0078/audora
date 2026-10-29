// File: core/src/test/kotlin/io/github/skm0078/audora/core/home/HomeLayoutTest.kt
// Purpose: The "Make it yours" choices survive a save, a move, a toggle and a line from another build.

package io.github.skm0078.audora.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun `the default home is light`() {
        val home = HomeLayout()
        assertEquals(listOf(HomeSection.THIS_WEEK, HomeSection.CONTINUE, HomeSection.LINE_OF_THE_DAY), home.shownOrder)
        assertTrue(home.shows(HomeSection.SHELF_CHIPS))
        assertTrue(home.coverTint)
        assertEquals("", home.name)
    }

    @Test
    fun `every choice survives a save`() {
        val home = HomeLayout()
            .moved(HomeSection.RECENTLY_ADDED, up = true)
            .toggled(HomeSection.RECENTLY_ADDED)
            .toggled(HomeSection.SHELF_CHIPS)
            .named("Ria | the reader")
            .copy(coverTint = false)
        assertEquals(home, HomeLayout.decode(home.encode()))
    }

    @Test
    fun `a move stops at either end`() {
        val home = HomeLayout()
        assertEquals(home, home.moved(HomeSection.THIS_WEEK, up = true))
        assertEquals(home, home.moved(HomeSection.RECENTLY_ADDED, up = false))
        assertEquals(home, home.moved(HomeSection.SHELF_CHIPS, up = true))
        assertEquals(
            listOf(
                HomeSection.THIS_WEEK,
                HomeSection.LINE_OF_THE_DAY,
                HomeSection.CONTINUE,
                HomeSection.RECENTLY_ADDED,
            ),
            home.moved(HomeSection.CONTINUE, up = false).order,
        )
    }

    @Test
    fun `a toggle turns a section off and on again`() {
        val off = HomeLayout().toggled(HomeSection.CONTINUE)
        assertFalse(off.shows(HomeSection.CONTINUE))
        assertTrue(off.toggled(HomeSection.CONTINUE).shows(HomeSection.CONTINUE))
    }

    @Test
    fun `a name is trimmed, kept on one line and cut to fit`() {
        assertEquals("Ria", HomeLayout().named("  Ria\n").name)
        assertEquals(HomeLayout.NAME_MAX, HomeLayout().named("x".repeat(HomeLayout.NAME_MAX + 9)).name.length)
        assertEquals("", HomeLayout().named("   ").name)
    }

    @Test
    fun `a missing or foreign line is the default home`() {
        assertEquals(HomeLayout(), HomeLayout.decode(null))
        assertEquals(HomeLayout(), HomeLayout.decode("not a home"))
    }

    @Test
    fun `unknown sections are dropped and missing ones added at the end`() {
        val home = HomeLayout.decode("RECENTLY_ADDED,FUTURE_THING,SHELF_CHIPS||tint|")
        assertEquals(
            listOf(
                HomeSection.RECENTLY_ADDED,
                HomeSection.THIS_WEEK,
                HomeSection.CONTINUE,
                HomeSection.LINE_OF_THE_DAY,
            ),
            home.order,
        )
        assertTrue(home.shows(HomeSection.RECENTLY_ADDED))
    }

    @Test
    fun `a home saved before This week came gets it at the end, shown`() {
        val home = HomeLayout.decode("LINE_OF_THE_DAY,CONTINUE,RECENTLY_ADDED|RECENTLY_ADDED|tint|")
        assertEquals(HomeSection.THIS_WEEK, home.order.last())
        assertTrue(home.shows(HomeSection.THIS_WEEK))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an order that leaves a section out is refused`() {
        HomeLayout(order = listOf(HomeSection.CONTINUE))
    }
}
