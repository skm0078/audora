// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/sync/SyncBookFilesTest.kt
// Purpose: A book's copy in the sync folder is named the same on every device and is a safe file name.

package io.github.skm0078.audora.playback.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBookFilesTest {
    @Test
    fun `the tag is the same for the same fingerprint and differs for another`() {
        assertEquals(SyncBookFiles.tagOf("v1:abc"), SyncBookFiles.tagOf("v1:abc"))
        assertNotEquals(SyncBookFiles.tagOf("v1:abc"), SyncBookFiles.tagOf("v1:abd"))
        assertEquals(10, SyncBookFiles.tagOf("v1:abc").length)
    }

    @Test
    fun `a name keeps the title, the tag and the extension`() {
        val name = SyncBookFiles.nameFor("Gitanjali", "v1:abc", "epub")
        assertEquals("Gitanjali [${SyncBookFiles.tagOf("v1:abc")}].epub", name)
    }

    @Test
    fun `characters a folder cannot hold are replaced, and a blank title still names the file`() {
        val name = SyncBookFiles.nameFor("a/b:c [d]?", "v1:abc", null)
        assertFalse(name.substringBefore(" [").any { it in "/:[]?" })
        assertTrue(SyncBookFiles.nameFor("  ", "v1:abc", "pdf").startsWith("Book ["))
    }
}
