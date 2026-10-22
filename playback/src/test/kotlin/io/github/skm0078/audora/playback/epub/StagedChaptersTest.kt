// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/StagedChaptersTest.kt
// Purpose: A talking book's staged chapters read back whole; damaged records and missing audio are rendered again.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.ExportedChapter
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.queue.UnitKey
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedChaptersTest {
    private val root = Files.createTempDirectory("staged").toFile()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a chapter staged by the recorder reads back with its timings and sentences`() {
        val folder = StagedChapters.folderFor(root, "book-1", "sig-a")
        val staged = StagedChapters(folder)
        val recorder = OverlayRecorder(folder, onChapter = { staged.save(it) })
        val chapter = AudioChapter(1, 0, 1)
        recorder.onSentenceWritten(chapter, sentence(0, 0, "A."), pcm(300))
        recorder.onSentenceWritten(chapter, sentence(1, 2, "B."), pcm(200))
        File(folder, "01 Pages 1-2.m4a").writeBytes(byteArrayOf(1, 2, 3))
        recorder.onChapterDone(ExportedChapter(chapter, "01 Pages 1-2.m4a", 500))

        val loaded = StagedChapters(folder).load().getValue(1)

        assertEquals(recorder.chapters.getValue(1), loaded.audio)
        assertEquals(listOf(UnitKey(0, 0), UnitKey(1, 2)), loaded.voiced)
    }

    @Test
    fun `restored chapters count as done and voiced`() {
        val folder = StagedChapters.folderFor(root, "book-1", "sig-a")
        val audio = ChapterAudio(2, File(folder, "02.m4a"), 24_000, listOf(SentenceClip("s3-0", 0, 10)))
        val recorder = OverlayRecorder(folder)

        recorder.restore(listOf(StagedChapter(audio, listOf(UnitKey(3, 0)))))

        assertEquals(audio, recorder.chapters.getValue(2))
        assertTrue(UnitKey(3, 0) in recorder.voiced)
    }

    @Test
    fun `a chapter whose audio is gone or whose record is damaged is not loaded`() {
        val folder = StagedChapters.folderFor(root, "book-1", "sig-a")
        val staged = StagedChapters(folder)
        staged.save(StagedChapter(ChapterAudio(1, File(folder, "gone.m4a"), 22_050, emptyList()), emptyList()))
        File(folder, "chapter-2.staged").writeText("audora-staged-chapter 1\nchapter two\n")
        File(folder, "chapter-3.staged").writeText("some other version\n")

        assertTrue(staged.load().isEmpty())
    }

    @Test
    fun `another book's or signature's staging is deleted`() {
        val old = StagedChapters.folderFor(root, "book-1", "sig-a")
        File(old, "01.m4a").writeBytes(byteArrayOf(1))

        val current = StagedChapters.folderFor(root, "book-1", "sig-b")

        assertFalse(old.exists())
        assertTrue(current.isDirectory)
        assertEquals(listOf(current.name), root.list()?.toList())
    }
}
