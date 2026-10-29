// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryShelvesTest.kt
// Purpose: The sidebar's collections keep the library's order, count only the books the listener can see, and wear
//          the colour and emoji the listener gave them.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.ShelfColour
import io.github.skm0078.audora.core.library.ShelfEmoji
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.ui.shell.Shelf
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelvesTest {
    private val school = BookCollection("c1", "For school", createdAtMillis = 1)
    private val bedtime = BookCollection("c2", "Bedtime", createdAtMillis = 2)
    private val empty = BookCollection("c3", "Someday", createdAtMillis = 3)

    @Test
    fun eachCollectionCountsItsBooksInTheLibrarysOrder() {
        val members = mapOf(
            BookId("a") to setOf("c1", "c2"),
            BookId("b") to setOf("c1"),
        )
        val shelves = shelvesOf(listOf(bedtime, school, empty), members, listOf(BookId("a"), BookId("b")))
        assertEquals(
            listOf(Shelf("c2", "Bedtime", 1), Shelf("c1", "For school", 2), Shelf("c3", "Someday", 0)),
            shelves,
        )
    }

    @Test
    fun aBookBeingRemovedIsNotCounted() {
        val members = mapOf(BookId("a") to setOf("c1"), BookId("gone") to setOf("c1"))
        assertEquals(listOf(Shelf("c1", "For school", 1)), shelvesOf(listOf(school), members, listOf(BookId("a"))))
    }

    @Test
    fun eachCollectionWearsItsLookAndOneWithoutOneHasTheDefault() {
        val look = ShelfLook(ShelfColour.entries.first(), ShelfEmoji.entries.first())
        val shelves = shelvesOf(listOf(bedtime, school), emptyMap(), emptyList(), mapOf("c2" to look))
        assertEquals(listOf(look, ShelfLook.DEFAULT), shelves.map { it.look })
    }
}
