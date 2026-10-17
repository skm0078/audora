// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrNoteMarkersTest.kt
// Purpose: Note markers OCR glued to words or set raised beside them are left out; figures and references stay (RI-5a).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.pdf.render.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrNoteMarkersTest {
    private val markers = OcrNoteMarkers(NoteConfig())
    private val upright: (ViewRect) -> ViewRect = { it }

    /** Words side by side on one line, each [WIDTH] wide and [HEIGHT] high, [GAP] apart. */
    private fun line(vararg texts: String) = texts.mapIndexed { i, text ->
        val left = i * (WIDTH + GAP)
        OcrWord(text, ViewRect(left, TOP, left + WIDTH, TOP + HEIGHT), CONFIDENT)
    }

    private fun texts(words: List<OcrWord>) = words.map { it.text }

    @Test
    fun `note signs glued to a word are left out, punctuation after them kept`() {
        val cases = mapOf(
            "Vaswani*" to "Vaswani",
            "Eich*," to "Eich,",
            "Gal**," to "Gal,",
            "Haghighat®," to "Haghighat,",
            "Smith#," to "Smith,",
            "Corporation™" to "Corporation",
            "both.²" to "both.",
            "Gilbertson'®" to "Gilbertson'",
            "बंगाल*" to "बंगाल",
            "double.!" to "double.",
            // Kept: digits glued to a word are references, a one-letter word is a name, a sign alone is not glued.
            "Fig.1" to "Fig.1",
            "C#" to "C#",
            "*" to "*",
            "Hello!" to "Hello!",
        )
        for ((read, expected) in cases) {
            assertEquals(
                read,
                listOf("A", expected),
                texts(markers.silence(line("A", read), upright)),
            )
        }
    }

    @Test
    fun `a small raised sign right after a word is left out, a raised number or a sign at the line start is kept`() {
        val words = line("Andreas", "Gal", "and", "12", "more")
        val raised = listOf(
            OcrWord(
                "†",
                ViewRect(
                    words[1].box!!.right + GAP / 4,
                    TOP - RISE,
                    words[1].box!!.right + GAP,
                    TOP + HEIGHT / 2,
                ),
            ),
            OcrWord(
                "3",
                ViewRect(
                    words[3].box!!.right + GAP / 4,
                    TOP - RISE,
                    words[3].box!!.right + GAP,
                    TOP + HEIGHT / 2,
                ),
            ),
        )
        val withMarkers = listOf(words[0], words[1], raised[0], words[2], words[3], raised[1], words[4])
        assertEquals(listOf("Andreas", "Gal", "and", "12", "3", "more"), texts(markers.silence(withMarkers, upright)))
        val bullet = listOf(OcrWord("*", ViewRect(0f, TOP - RISE, GAP, TOP + HEIGHT / 2))) + line("x", "Leaves", "fall")
        assertEquals("*", texts(markers.silence(bullet, upright)).first())
    }

    @Test
    fun `switched off, nothing is left out`() {
        val off = OcrNoteMarkers(NoteConfig(silenceMarkers = false))
        assertEquals(listOf("A", "Vaswani*"), texts(off.silence(line("A", "Vaswani*"), upright)))
    }

    private companion object {
        const val TOP = 0.5f
        const val WIDTH = 0.08f
        const val HEIGHT = 0.02f
        const val GAP = 0.01f
        const val RISE = 0.006f
        const val CONFIDENT = 95
    }
}
