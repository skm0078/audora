// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/TalkingBookLeftoversTest.kt
// Purpose: A talking book's staged chapters are kept a week from their newest write, then deleted; a gone book's
//          at once.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkingBookLeftoversTest {
    private val root = Files.createTempDirectory("talking-book").toFile()
    private val leftovers = TalkingBookLeftovers(root)
    private val now = 100L * DAY
    private val policy = LeftoverPolicy(LeftoverConfig(keepMs = 7 * DAY), now)
    private val library = setOf(StagedChapters.bookPrefix(BOOK))

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A staged book: an old chapter and one written [newestDaysAgo]. */
    private fun staged(book: String, newestDaysAgo: Long): File {
        val folder = StagedChapters.folderFor(root, book, "signature")
        File(folder, "chapter-1.m4a").apply { writeBytes(ByteArray(60)) }.setLastModified(now - 30 * DAY)
        File(folder, "chapter-2.m4a").apply { writeBytes(ByteArray(40)) }.setLastModified(now - newestDaysAgo * DAY)
        folder.setLastModified(now - 30 * DAY)
        return folder
    }

    @Test
    fun `a chapter staged this week keeps the whole book for Continue`() {
        val folder = staged(BOOK, newestDaysAgo = 2)

        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, library, asked = false))
        assertTrue(folder.exists())
        assertEquals(LeftoverTally.of(LeftoverKind.TALKING_BOOK, 1, 100L), leftovers.measure())
        assertEquals(100L, leftovers.bytesFor(BOOK))
    }

    @Test
    fun `a book staged over a week ago is deleted and counted`() {
        val folder = staged(BOOK, newestDaysAgo = 8)

        assertEquals(LeftoverTally.of(LeftoverKind.TALKING_BOOK, 1, 100L), leftovers.clean(policy, library, false))
        assertFalse(folder.exists())
    }

    @Test
    fun `a book gone from the library loses its staging at once, and asking deletes any`() {
        val gone = staged("gone", newestDaysAgo = 0)
        assertEquals(1, leftovers.clean(policy, library, asked = false).count(LeftoverKind.TALKING_BOOK))
        assertFalse(gone.exists())

        val fresh = staged(BOOK, newestDaysAgo = 0)
        assertEquals(1, leftovers.clean(policy, library, asked = true).count(LeftoverKind.TALKING_BOOK))
        assertFalse(fresh.exists())
    }

    @Test
    fun `the card's delete takes only that book`() {
        staged(BOOK, newestDaysAgo = 0)

        assertEquals(LeftoverTally.NONE, leftovers.discard("other"))
        assertEquals(1, leftovers.discard(BOOK).count(LeftoverKind.TALKING_BOOK))
        assertEquals(0L, leftovers.bytesFor(BOOK))
    }

    @Test
    fun `no staging folder at all finds nothing`() {
        root.deleteRecursively()
        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, library, asked = true))
        assertEquals(LeftoverTally.NONE, leftovers.measure())
    }

    private companion object {
        const val BOOK = "book"
        const val DAY = 24L * 60 * 60 * 1000
    }
}
