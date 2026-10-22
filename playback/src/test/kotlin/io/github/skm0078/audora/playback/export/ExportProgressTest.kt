// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/ExportProgressTest.kt
// Purpose: Progress counts pages across chapters and reports only whole steps, always ending at 100.

package io.github.skm0078.audora.playback.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportProgressTest {
    @Test
    fun `reports each step across chapters and ends at 100`() {
        val progress = ExportProgress(listOf(AudioChapter(1, 0, 3), AudioChapter(2, 4, 7)), stepPercent = 25)

        val reports = List(8) { progress.onPageDone() }

        assertEquals(listOf(null, 25, null, 50, null, 75, null, 100), reports)
    }

    @Test
    fun `small steps on a long book skip pages that do not move the percentage`() {
        val progress = ExportProgress(listOf(AudioChapter(1, 0, 299)), stepPercent = 1)

        val reports = List(300) { progress.onPageDone() }.filterNotNull()

        assertEquals((1..100).toList(), reports)
    }

    @Test
    fun `pages kept from an earlier run count as done without a report`() {
        val progress = ExportProgress(listOf(AudioChapter(1, 0, 3), AudioChapter(2, 4, 7)), stepPercent = 25)

        progress.skip(4)

        assertEquals(50, progress.percent)
        assertEquals(listOf(null, 75, null, 100), List(4) { progress.onPageDone() })
    }
}
