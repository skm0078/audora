// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/AudioChaptersTest.kt
// Purpose: Outline chapters become audiobook files without losing front matter; books without one get even parts.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChaptersTest {
    private val parts = AudiobookConfig(pagesPerPartWithoutChapters = 10)

    private fun ranges(starts: List<Int>, pageCount: Int): List<IntRange> =
        AudioChapters.plan(starts, pageCount, parts).map { it.pages }

    @Test
    fun `table of chapter plans`() {
        data class Case(val name: String, val starts: List<Int>, val pageCount: Int, val expected: List<IntRange>)
        val cases = listOf(
            Case("outline from page 0", listOf(0, 5, 12), 20, listOf(0..4, 5..11, 12..19)),
            Case("front matter kept as its own file", listOf(3, 9), 15, listOf(0..2, 3..8, 9..14)),
            Case("no outline: parts of 10 pages", emptyList(), 25, listOf(0..9, 10..19, 20..24)),
            Case("outline with only page 0 counts as none", listOf(0), 12, listOf(0..9, 10..11)),
            Case("starts outside the book are ignored", listOf(-1, 4, 40), 8, listOf(0..3, 4..7)),
            Case("unsorted duplicates", listOf(6, 2, 6), 9, listOf(0..1, 2..5, 6..8)),
            Case("last page starts a chapter", listOf(4), 5, listOf(0..3, 4..4)),
            Case("one page book", emptyList(), 1, listOf(0..0)),
            Case("empty book", listOf(0, 3), 0, emptyList()),
        )
        for (case in cases) assertEquals(case.name, case.expected, ranges(case.starts, case.pageCount))
    }

    @Test
    fun `chapters are numbered from one in reading order`() {
        val numbers = AudioChapters.plan(listOf(2, 7), 10, parts).map { it.number }
        assertEquals(listOf(1, 2, 3), numbers)
    }

    @Test
    fun `file names sort in reading order and show one-based pages`() {
        assertEquals("03 Pages 41-58", AudioChapter(3, 40, 57).fileStem(total = 12))
        assertEquals("003 Page 9", AudioChapter(3, 8, 8).fileStem(total = 120))
        assertEquals("1 Pages 1-4", AudioChapter(1, 0, 3).fileStem(total = 9))
    }
}
