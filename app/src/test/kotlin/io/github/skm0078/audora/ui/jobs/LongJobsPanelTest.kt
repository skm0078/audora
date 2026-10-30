// File: app/src/test/kotlin/io/github/skm0078/audora/ui/jobs/LongJobsPanelTest.kt
// Purpose: Which long jobs the library and a book's screens show, the next step under a result, the card's tone,
//          which jobs offer their unfinished files, and a download's percent.

package io.github.skm0078.audora.ui.jobs

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.speech.download.DownloadJob
import io.github.skm0078.audora.speech.download.DownloadKind
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `a card's tone follows how its job is going`() {
        val running = jobs.getValue(LongJobKind.AUDIOBOOK)
        assertEquals(JobTone.RUNNING, toneOf(running))
        assertEquals(JobTone.WAITING, toneOf(running.copy(waitingForCharger = true)))
        assertEquals(JobTone.DONE, toneOf(running.copy(end = LongJobEnd.DONE)))
        assertEquals(JobTone.FAILED, toneOf(running.copy(end = LongJobEnd.FAILED)))
        assertEquals(JobTone.STOPPED, toneOf(running.copy(end = LongJobEnd.STOPPED)))
    }

    @Test
    fun `only a stopped or failed audiobook or talking book offers its unfinished files`() {
        val audiobook = jobs.getValue(LongJobKind.AUDIOBOOK)
        val studio = jobs.getValue(LongJobKind.STUDIO)
        assertFalse(leavesUnfinishedFiles(audiobook))
        assertFalse(leavesUnfinishedFiles(audiobook.copy(end = LongJobEnd.DONE)))
        assertTrue(leavesUnfinishedFiles(audiobook.copy(end = LongJobEnd.FAILED)))
        assertTrue(leavesUnfinishedFiles(audiobook.copy(kind = LongJobKind.COPY, end = LongJobEnd.STOPPED)))
        assertFalse(leavesUnfinishedFiles(studio.copy(end = LongJobEnd.STOPPED)))
    }

    @Test
    fun `a download's percent is whole, never above 100, and 0 before its size is known`() {
        val download = DownloadJob(UUID.randomUUID(), DownloadKind.VOICE, "v", running = true)
        assertEquals(0, percentOf(download))
        assertEquals(28, percentOf(download.copy(bytes = 34L, total = 120L)))
        assertEquals(99, percentOf(download.copy(bytes = 999L, total = 1_000L)))
        assertEquals(100, percentOf(download.copy(bytes = 2_000L, total = 1_000L)))
    }
}
