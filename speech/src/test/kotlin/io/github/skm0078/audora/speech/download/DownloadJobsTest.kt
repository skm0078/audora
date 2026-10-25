// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/download/DownloadJobsTest.kt
// Purpose: The Jobs screen reads each download's kind, item and progress from its WorkManager tags (ADR-067).

package io.github.skm0078.audora.speech.download

import androidx.work.Data
import androidx.work.WorkInfo
import io.github.skm0078.audora.speech.dictionary.DictionaryDownloadWorker
import io.github.skm0078.audora.speech.voices.VoiceDownloadWorker
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadJobsTest {
    private val id = UUID.randomUUID()

    @Test
    fun `a running voice download has its voice and progress`() {
        val progress = Data.Builder().putLong("bytes", 40L).putLong("total", 100L).build()
        val tags = setOf(VoiceDownloadWorker::class.java.name, DownloadJobs.itemTag("en-bruno"), "other")
        val job = DownloadJobs.jobOf(WorkInfo(id, WorkInfo.State.RUNNING, tags, progress = progress))
        assertEquals(DownloadJob(id, DownloadKind.VOICE, "en-bruno", running = true, bytes = 40L, total = 100L), job)
    }

    @Test
    fun `a waiting download shows no progress`() {
        val tags = setOf(DictionaryDownloadWorker::class.java.name, DownloadJobs.itemTag("bn-IN"))
        val job = DownloadJobs.jobOf(WorkInfo(id, WorkInfo.State.ENQUEUED, tags))
        assertFalse(job!!.running)
        assertEquals(DownloadKind.DICTIONARY, job.kind)
        assertEquals("bn-IN", job.item)
        assertEquals(0L, job.total)
    }

    @Test
    fun `a download queued before items were tagged still shows, without its item`() {
        val job = DownloadJobs.jobOf(WorkInfo(id, WorkInfo.State.RUNNING, setOf(VoiceDownloadWorker::class.java.name)))
        assertNull(job!!.item)
    }

    @Test
    fun `other work is not a download`() {
        assertNull(DownloadJobs.jobOf(WorkInfo(id, WorkInfo.State.RUNNING, setOf("some.other.Worker"))))
    }

    @Test
    fun `every kind has its own worker`() {
        assertTrue(DownloadKind.entries.map { it.worker }.toSet().size == DownloadKind.entries.size)
    }
}
