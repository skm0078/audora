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
        assertEquals(
            listOf(HomeSection.CONTINUE, HomeSection.LINE_OF_THE_DAY, HomeSection.FROM_LISTENLOCK),
            home.shownOrder,
        )
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
        assertEquals(home, home.moved(HomeSection.CONTINUE, up = true))
        assertEquals(home, home.moved(HomeSection.FROM_LISTENLOCK, up = false))
        assertEquals(home, home.moved(HomeSection.SHELF_CHIPS, up = true))
        assertEquals(
            listOf(
                HomeSection.LINE_OF_THE_DAY,
                HomeSection.CONTINUE,
                HomeSection.RECENTLY_ADDED,
                HomeSection.FROM_LISTENLOCK,
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
                HomeSection.CONTINUE,
                HomeSection.LINE_OF_THE_DAY,
                HomeSection.FROM_LISTENLOCK,
            ),
            home.order,
        )
        assertTrue(home.shows(HomeSection.RECENTLY_ADDED))
    }

    @Test
    fun `a home saved before From ListenLock existed shows it last`() {
        val saved = "LINE_OF_THE_DAY,RECENTLY_ADDED,CONTINUE|RECENTLY_ADDED|tint|Ria"
        val home = HomeLayout.decode(saved)
        assertEquals(HomeSection.FROM_LISTENLOCK, home.order.last())
        assertTrue(home.shows(HomeSection.FROM_LISTENLOCK))
        assertEquals("Ria", home.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an order that leaves a section out is refused`() {
        HomeLayout(order = listOf(HomeSection.CONTINUE))
    }
}
