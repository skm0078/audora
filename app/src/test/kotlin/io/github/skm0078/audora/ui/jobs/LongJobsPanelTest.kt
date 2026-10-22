// File: app/src/test/kotlin/io/github/skm0078/audora/ui/jobs/LongJobsPanelTest.kt
// Purpose: Which long jobs the library and a book's screens show, and the next step under a result.

package io.github.skm0078.audora.ui.jobs

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongJobsPanelTest {
    private val a = BookId("a")
    private val b = BookId("b")
    private val jobs = mapOf(
        LongJobKind.STUDIO to LongJob(LongJobKind.STUDIO, b, "Studio", "Ready: 1 h"),
        LongJobKind.AUDIOBOOK to LongJob(LongJobKind.AUDIOBOOK, a, "Saving", "Chapter 1", percent = 3),
    )

    @Test
    fun `the library shows every job in a fixed order, a book's screen only its own`() {
        assertEquals(listOf(LongJobKind.AUDIOBOOK, LongJobKind.STUDIO), shownJobs(jobs, null).map { it.kind })
        assertEquals(listOf(LongJobKind.STUDIO), shownJobs(jobs, b).map { it.kind })
        assertEquals(emptyList<LongJob>(), shownJobs(jobs, BookId("c")))
    }

    @Test
    fun `a failed or stopped job says what to do next, a done one says where it is`() {
        assertEquals(R.string.long_job_failed_next, nextStep(LongJobEnd.FAILED))
        assertEquals(R.string.long_job_stopped_next, nextStep(LongJobEnd.STOPPED))
        assertNull(nextStep(LongJobEnd.DONE))
        assertNull(nextStep(null))
    }
}
