// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrClutterTest.kt
// Purpose: Running headers, footers and page numbers on scanned pages, found against neighbours' margin strips.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.pdf.render.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrClutterTest {
    private val clutter = OcrClutter(CleanupConfig())

    /** One line per (text, top), boxes spanning most of the width. */
    private fun page(vararg lines: Pair<String, Float>, quarterTurns: Int = 0): OcrText = OcrText(
        lines.mapIndexed { i, (text, top) ->
            val box = turnView(ViewRect(0.1f, top, 0.9f, top + LINE), OcrText.FULL_TURN - quarterTurns)
            OcrLine(text.split(" ").map { OcrWord(it, box) }, paragraph = i)
        },
        quarterTurns,
    )

    private val body = arrayOf(
        "The body of the page goes on here." to 0.3f,
        "And on to a second line of the body." to 0.5f,
    )

    private fun strips(vararg byOffset: Pair<Int, OcrMargins>): () -> Map<Int, OcrMargins> = { byOffset.toMap() }

    @Test
    fun `a header repeated on the neighbours is dropped even with a letter misread`() {
        val text = page("THE HISTORY OF BENGAL" to 0.03f, *body)
        val neighbours = strips(
            -2 to OcrMargins(listOf("THE HISTORY OF BENGAI"), emptyList()),
            -1 to OcrMargins(listOf("CHAPTER TWO"), emptyList()),
            1 to OcrMargins(listOf("CHAPTER TWO"), emptyList()),
            2 to OcrMargins(listOf("THE HlSTORY OF BENGAL"), emptyList()),
        )
        assertEquals(setOf(0), clutter.droppedLines(text, neighbours))
    }

    @Test
    fun `a line seen on only one neighbour of four is kept`() {
        val text = page("THE HISTORY OF BENGAL" to 0.03f, *body)
        val other = OcrMargins(listOf("CHAPTER TWO"), emptyList())
        val neighbours = strips(
            -2 to other,
            -1 to OcrMargins(listOf("THE HISTORY OF BENGAL"), emptyList()),
            1 to other,
            2 to other,
        )
        assertEquals(emptySet<Int>(), clutter.droppedLines(text, neighbours))
    }

    @Test
    fun `page numbers go, alone or stepping with the page`() {
        val text = page(*body, "Page 12 of the book" to 0.95f, "12" to 0.97f)
        val neighbours = strips(1 to OcrMargins(emptyList(), listOf("Page 13 of the book")))
        assertEquals(setOf(2, 3), clutter.droppedLines(text, neighbours))
    }

    @Test
    fun `a printer's slug at the foot goes, the same words in the body stay`() {
        val slugs = listOf("HUD3_25_09Single-Bengali-BE.indd Sec1:9", "ch04.qxd 2/3/09 10:12 AM Page 62")
        for (slug in slugs) {
            assertEquals(slug, setOf(2), clutter.droppedLines(page(*body, slug to 0.96f), strips()))
        }
        // A print date and time alone is a slug only when a neighbour's strip carries the same one.
        val printed = strips(1 to OcrMargins(emptyList(), listOf("5/13/11, 6:45 AM")))
        assertEquals(setOf(2), clutter.droppedLines(page(*body, "5/13/11 6:45 AM" to 0.96f), printed))
        val laterPrint = strips(1 to OcrMargins(emptyList(), listOf("5/13/11 6:46 AM")))
        assertEquals(emptySet<Int>(), clutter.droppedLines(page(*body, "5/13/11 6:45 AM" to 0.96f), laterPrint))
        val inBody = page("Save it as report.indd before 5/13/11 6:45 AM" to 0.4f, *body)
        assertEquals(emptySet<Int>(), clutter.droppedLines(inBody, strips()))
    }

    @Test
    fun `a date and time in the margin band is kept without a slug on the neighbours`() {
        val lines = listOf(
            "Physics 15/03/2024 10:30" to 0.96f,
            "दिनांक 12.05.2019, 10:30 बजे" to 0.03f,
            "दिनांक १२.०५.२०१९, १०:३० बजे" to 0.96f,
            "তারিখ ১২/০৫/২০১৯ ১০:৩০" to 0.97f,
        )
        // A date sheet's next page holds the next exam, at the same hour.
        val neighbours = strips(1 to OcrMargins(listOf("Notice"), listOf("Chemistry 16/03/2024 10:30")))
        for (line in lines) {
            assertEquals(line.first, emptySet<Int>(), clutter.droppedLines(page(*body, line), neighbours))
        }
    }

    @Test
    fun `body lines and pages without body text are never touched, and neighbours are not read for them`() {
        var asked = false
        val neighbours = {
            asked = true
            emptyMap<Int, OcrMargins>()
        }
        assertEquals(emptySet<Int>(), clutter.droppedLines(page(*body), neighbours))
        assertEquals(emptySet<Int>(), clutter.droppedLines(page("12" to 0.03f), neighbours))
        assertTrue(!asked)
    }

    @Test
    fun `a sideways scan's header is found in its reading frame`() {
        val text = page("THE HISTORY OF BENGAL" to 0.03f, *body, quarterTurns = 1)
        val same = OcrMargins(listOf("THE HISTORY OF BENGAL"), emptyList())
        assertEquals(setOf(0), clutter.droppedLines(text, strips(-1 to same, 1 to same)))
    }

    private companion object {
        const val LINE = 0.02f
    }
}
