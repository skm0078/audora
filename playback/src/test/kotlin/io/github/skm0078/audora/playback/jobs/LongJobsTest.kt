// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/jobs/LongJobsTest.kt
// Purpose: What the app shows of a long job: only a begun job is published, results stay until dismissed.

package io.github.skm0078.audora.playback.jobs

import io.github.skm0078.audora.core.library.BookId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongJobsTest {
    private val book = BookId("book-1")
    private val kind = LongJobKind.AUDIOBOOK

    @After
    fun clear() {
        LongJobKind.entries.forEach { k ->
            LongJobs.finish(k, LongJobEnd.STOPPED, "", "")
            LongJobs.dismiss(k)
        }
    }

    @Test
    fun `progress before a job begins is not shown`() {
        LongJobs.progress(kind, "Saving", "Chapter 1", 10)
        assertNull(LongJobs.jobs.value[kind])
    }

    @Test
    fun `a begun job shows its progress, then its result until dismissed`() {
        LongJobs.begin(kind, book, "Saving", "Preparing")
        assertNull(LongJobs.jobs.value.getValue(kind).percent)
        LongJobs.progress(kind, "Saving “A”", "Chapter 2 of 5", 40)
        val running = LongJobs.jobs.value.getValue(kind)
        assertEquals(40, running.percent)
        assertTrue(running.running)
        LongJobs.dismiss(kind) // a running job cannot be dismissed
        assertTrue(LongJobs.jobs.value.containsKey(kind))
        LongJobs.finish(kind, LongJobEnd.DONE, "Saved “A”", "5 files in Audiobooks/A")
        val done = LongJobs.jobs.value.getValue(kind)
        assertEquals(LongJobEnd.DONE, done.end)
        assertEquals(book, done.bookId)
        assertFalse(done.running)
        LongJobs.dismiss(kind)
        assertNull(LongJobs.jobs.value[kind])
    }

    @Test
    fun `a refused start's result does not replace a finished job's result`() {
        LongJobs.begin(kind, book, "Saving", "Preparing")
        LongJobs.finish(kind, LongJobEnd.DONE, "Saved", "Done")
        LongJobs.finish(kind, LongJobEnd.FAILED, "Busy", "Another export runs")
        assertEquals(LongJobEnd.DONE, LongJobs.jobs.value.getValue(kind).end)
    }

    @Test
    fun `kinds are separate`() {
        LongJobs.begin(LongJobKind.STUDIO, book, "Studio", "Preparing")
        LongJobs.progress(LongJobKind.STUDIO, "Studio", "Waiting for the charger", 5, waitingForCharger = true)
        assertTrue(LongJobs.jobs.value.getValue(LongJobKind.STUDIO).waitingForCharger)
        assertNull(LongJobs.jobs.value[LongJobKind.COPY])
    }
}
