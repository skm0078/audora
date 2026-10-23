// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/OverlayRecorderTest.kt
// Purpose: Sentence timings recorded from the exporter, per chapter file, in samples.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.ExportedChapter
import io.github.skm0078.audora.playback.pcm
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRecorderTest {
    @Test
    fun `each chapter's clips start at zero and follow each other`() {
        val folder = File("/tmp/talking")
        val recorder = OverlayRecorder(folder)
        val first = AudioChapter(1, 0, 0)
        val second = AudioChapter(2, 1, 1)
        recorder.onSentenceWritten(first, sentence(0, 0, "A."), pcm(300))
        recorder.onSentenceWritten(first, sentence(0, 1, "B."), pcm(200))
        recorder.onChapterDone(ExportedChapter(first, "01 Page 1.m4a", 500))
        recorder.onSentenceWritten(second, sentence(1, 0, "C."), pcm(100))
        recorder.onChapterDone(ExportedChapter(second, "02 Page 2.m4a", 100))

        val one = recorder.chapters.getValue(1)
        assertEquals(File(folder, "01 Page 1.m4a"), one.file)
        assertEquals(listOf(SentenceClip("s0-0", 0, 300), SentenceClip("s0-1", 300, 500)), one.clips)
        assertEquals(listOf(SentenceClip("s1-0", 0, 100)), recorder.chapters.getValue(2).clips)
    }
}
