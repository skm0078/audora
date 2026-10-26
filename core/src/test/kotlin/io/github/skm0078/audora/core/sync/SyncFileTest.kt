// File: core/src/test/kotlin/io/github/skm0078/audora/core/sync/SyncFileTest.kt
// Purpose: A phone's sync file and memory survive a write and a read, notes with tabs and line breaks included, and
//          a damaged line or a newer format costs only what it must.

package io.github.skm0078.audora.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncFileTest {
    private val anchor = SyncAnchor(page = 3, charStart = 120, fingerprint = -42L, tailFingerprint = 7L, 13, 1_000L)

    private val snapshot = SyncSnapshot(
        deviceId = "4f1c",
        writtenAtMillis = 5_000L,
        books = listOf(
            SyncBook(
                fingerprint = "v1:abc",
                title = "Gitanjali\tপ্রথম",
                fileName = "Gitanjali [abc].epub",
                progress = anchor,
                bookmarks = listOf(SyncBookmark(anchor, "line one\nline two \\ done")),
                highlights = listOf(SyncHighlight(anchor, 4, 10, "quote\twith tab", "", "GREEN")),
            ),
            SyncBook(fingerprint = "v1:def", title = "No place yet"),
        ),
        removals = listOf(SyncRemoval("v1:abc|b:1:2:3", 4_000L)),
    )

    @Test
    fun `a snapshot reads back as written`() {
        assertEquals(snapshot, SyncFile.decode(SyncFile.encode(snapshot)))
    }

    @Test
    fun `memory reads back as written, and a missing one is a first sync`() {
        val memory = SyncMemory(
            knownMarks = setOf("v1:abc|b:1:2:3", "v1:abc|h:1:2:3:4:5"),
            removals = listOf(SyncRemoval("v1:x|b:0:0:9", 9L)),
            knownBooks = setOf("v1:abc"),
            dismissedBooks = setOf("v1:old"),
        )

        assertEquals(memory, SyncFile.decodeMemory(SyncFile.encode(memory)))
        assertEquals(SyncMemory(), SyncFile.decodeMemory(null))
        assertEquals(SyncMemory(), SyncFile.decodeMemory("garbage"))
    }

    @Test
    fun `a damaged line is skipped and the rest is read`() {
        val text = SyncFile.encode(snapshot).replace("bookmark\t3\t120", "bookmark\tthree\t120")

        val read = SyncFile.decode(text)

        assertEquals(emptyList<SyncBookmark>(), read?.books?.first()?.bookmarks)
        assertEquals(snapshot.books.first().highlights, read?.books?.first()?.highlights)
    }

    @Test
    fun `a newer format, or not a sync file at all, is not read`() {
        assertNull(SyncFile.decode(SyncFile.encode(snapshot).replaceFirst("listenlock-sync\t1", "listenlock-sync\t2")))
        assertNull(SyncFile.decode("hello"))
        assertNull(SyncFile.decode(""))
    }

    @Test
    fun `escaping round-trips every special character`() {
        listOf("", "plain", "a\\b", "\\t literally", "tab\there", "\r\n", "end\\").forEach {
            assertEquals(it, SyncText.unescape(SyncText.escape(it)))
        }
    }
}
