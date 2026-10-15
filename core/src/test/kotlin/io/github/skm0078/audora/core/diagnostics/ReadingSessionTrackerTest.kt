// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/ReadingSessionTrackerTest.kt
// Purpose: A marker left by a dead process is reported once as unfinished; this process's own session never is.

package io.github.skm0078.audora.core.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingSessionTrackerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File
    private var now = 1_000L

    @Before
    fun setUp() {
        file = File(temp.root, "state.properties")
    }

    /** A new tracker over the same file is what a new process sees. */
    private fun newProcess() = ReadingSessionTracker(DiagnosticsStateStore(file)) { now }

    @Test
    fun `fresh install has nothing unfinished`() {
        assertNull(newProcess().unfinishedSession())
    }

    @Test
    fun `own playing session is not unfinished`() {
        val tracker = newProcess()
        tracker.onPlaybackActive("book", 3)

        assertNull(tracker.unfinishedSession())
    }

    @Test
    fun `process dying while playing leaves an unfinished session for the next process`() {
        newProcess().onPlaybackActive("book", 3)

        assertEquals(ReadingSession("book", 3, 1_000L), newProcess().unfinishedSession())
    }

    @Test
    fun `pausing or stopping before death leaves nothing unfinished`() {
        newProcess().apply {
            onPlaybackActive("book", 3)
            onPlaybackInactive()
        }

        assertNull(newProcess().unfinishedSession())
    }

    @Test
    fun `page change keeps the start time, a new book resets it`() {
        val tracker = newProcess()
        tracker.onPlaybackActive("book", 3)
        now = 5_000L
        tracker.onPlaybackActive("book", 4)
        assertEquals(ReadingSession("book", 4, 1_000L), newProcess().unfinishedSession())

        tracker.onPlaybackActive("other", 0)
        assertEquals(ReadingSession("other", 0, 5_000L), newProcess().unfinishedSession())
    }

    @Test
    fun `unfinished session survives repeated deaths until the user answers`() {
        newProcess().onPlaybackActive("book", 3)
        newProcess().unfinishedSession()

        val third = newProcess()
        assertEquals("book", third.unfinishedSession()?.bookId)

        third.resolveUnfinished(resumed = false)
        assertNull(third.unfinishedSession())
        assertNull(newProcess().unfinishedSession())
    }

    @Test
    fun `starting playback replaces the unfinished session`() {
        newProcess().onPlaybackActive("book", 3)
        val next = newProcess()
        now = 9_000L

        next.onPlaybackActive("book", 3)

        assertNull(next.unfinishedSession())
        assertEquals(9_000L, newProcess().unfinishedSession()?.startedAtMillis)
    }
}
