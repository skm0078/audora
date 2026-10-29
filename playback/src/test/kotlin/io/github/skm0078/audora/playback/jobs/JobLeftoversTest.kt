// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/jobs/JobLeftoversTest.kt
// Purpose: One clean-up over every kind of leftover: a kind whose job runs is left alone, what went is reported, and
//          the Jobs screen's state says what still waits.

package io.github.skm0078.audora.playback.jobs

import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.playback.epub.StagedChapters
import io.github.skm0078.audora.playback.epub.TalkingBookLeftovers
import io.github.skm0078.audora.playback.export.AudiobookLeftovers
import io.github.skm0078.audora.playback.export.ExportRecord
import io.github.skm0078.audora.playback.export.ExportRecords
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.export.SavedChapter
import io.github.skm0078.audora.playback.export.SavedFiles
import io.github.skm0078.audora.speech.download.DownloadLeftovers
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLeftoversTest {
    private val root = Files.createTempDirectory("leftovers").toFile()
    private val noBackup = File(root, "no_backup")
    private val records = ExportRecords(File(root, "exports"))
    private val staging = File(root, "talking-book")
    private val files = object : SavedFiles {
        val present = HashSet<String>()

        override fun exists(location: String) = location in present

        override fun delete(location: String): Boolean {
            present.remove(location)
            return true
        }

        override fun sizeOf(location: String) = if (location in present) SIZE else 0L
    }
    private val report = object : LeftoverReport {
        var last: Pair<LeftoverTally, Long>? = null

        override fun save(cleaned: LeftoverTally, atMs: Long) {
            last = cleaned to atMs
        }

        override fun load() = last
    }
    private var downloading = false
    private val now = 100L * DAY
    private val leftovers = JobLeftovers(
        DownloadLeftovers(noBackup) { downloading },
        AudiobookLeftovers(records, files),
        TalkingBookLeftovers(staging),
        report,
        config = LeftoverConfig(keepMs = 7 * DAY, startDelayMs = 0L),
        clock = { now },
    )
    private val known = JobLeftovers.Known(books = setOf(BOOK), made = emptySet())

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun oldDownload(): File = File(noBackup, "ocr/downloads/ocr-bn.partial").apply {
        parentFile.mkdirs()
        writeBytes(ByteArray(SIZE.toInt()))
        setLastModified(now - 30 * DAY)
    }

    private fun oldAudiobook() {
        files.present += "content://media/1"
        records.save(BOOK, ExportRecord("key", listOf(SavedChapter(1, "01.m4a", 1L, "content://media/1"))))
        File(root, "exports/${ExportRecords.hashOf(BOOK)}.txt").setLastModified(now - 30 * DAY)
    }

    private fun freshTalkingBook(): File = StagedChapters.folderFor(staging, BOOK, "sig").also { folder ->
        File(folder, "chapter-1.m4a").writeBytes(ByteArray(SIZE.toInt()))
        folder.walkBottomUp().forEach { it.setLastModified(now) }
    }

    @Test
    fun `every kind is cleaned in one go and the clean-up is reported`() = runTest {
        val download = oldDownload()
        oldAudiobook()
        val talking = freshTalkingBook()

        val cleaned = leftovers.clean(known, asked = false)

        assertEquals(1, cleaned.count(LeftoverKind.DOWNLOAD))
        assertEquals(1, cleaned.count(LeftoverKind.AUDIOBOOK))
        assertEquals(0, cleaned.count(LeftoverKind.TALKING_BOOK))
        assertTrue(!download.exists())
        assertNull(records.load(BOOK))
        assertTrue(talking.exists())
        assertEquals(cleaned to now, report.last)
        val state = leftovers.state.value
        assertEquals(LeftoverTally.of(LeftoverKind.TALKING_BOOK, 1, SIZE), state?.waiting)
        assertEquals(now, state?.lastCleanedAtMs)
        assertEquals(7, state?.keepDays)
    }

    @Test
    fun `a running export keeps its record and is not counted; a queued download keeps its file`() = runTest {
        val download = oldDownload()
        oldAudiobook()
        downloading = true
        val release = CountDownLatch(1)
        val holding = CountDownLatch(1)
        assertTrue(
            JobSlots.EXPORT.launch("export") {
                holding.countDown()
                release.await(5, TimeUnit.SECONDS)
            },
        )
        try {
            holding.await(1, TimeUnit.SECONDS)
            val cleaned = leftovers.clean(known, asked = true)

            assertEquals(LeftoverTally.NONE, cleaned)
            assertNotNull(records.load(BOOK))
            assertTrue(download.exists())
            assertNull(report.last)
            assertEquals(LeftoverTally.NONE, leftovers.state.value?.waiting)
        } finally {
            release.countDown()
            while (JobSlots.EXPORT.busy) Thread.sleep(WAIT_MS)
        }
    }

    private companion object {
        const val BOOK = "book"
        const val SIZE = 100L
        const val DAY = 24L * 60 * 60 * 1000
        const val WAIT_MS = 5L
    }
}
