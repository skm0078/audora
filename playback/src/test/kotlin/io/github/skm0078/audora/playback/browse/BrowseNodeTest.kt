// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/browse/BrowseNodeTest.kt
// Purpose: Every browse node round-trips through its media id; ids Audora never gave out are refused.

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseNodeTest {
    private val id = BookId("b/1:odd id")

    @Test
    fun `every node round-trips`() {
        val nodes = listOf(
            BrowseNode.Root,
            BrowseNode.Recent,
            BrowseNode.UpNext,
            BrowseNode.Shelf,
            BrowseNode.Book(id),
            BrowseNode.Resume(id),
            BrowseNode.Chapter(id, 12),
        )
        nodes.forEach { assertEquals(it, BrowseNode.parse(it.mediaId)) }
    }

    @Test
    fun `foreign or broken ids are refused`() {
        listOf("", "song/1", "book/", "resume/ ", "chapter/x/b1", "chapter/-2/b1", "chapter/3/", "chapter/3").forEach {
            assertNull(it, BrowseNode.parse(it))
        }
    }
}
