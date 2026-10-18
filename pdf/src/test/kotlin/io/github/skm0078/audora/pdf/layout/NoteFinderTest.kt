// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/NoteFinderTest.kt
// Purpose: Note markers fall silent, exponents stay, footnotes and "Note:" paragraphs are tagged (RI-5a).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BODY
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFinderTest {
    private val builder = ReadingUnitBuilder()

    private fun units(sketch: PageSketch): List<ReadingUnit> = builder.build(sketch.build())

    /** A page of body text (it sets the body size), then [pieces] as one line, then body text below it. */
    private fun withLine(pieces: List<Triple<String, Float, Float>>): String {
        val sketch = PageSketch()
        val below = sketch.lines(List(3) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        sketch.mixed(pieces, LEFT, below)
        sketch.lines(List(3) { "More body text in the regular size follows it, line $it." }, LEFT, below - PITCH)
        return builder.readingText(sketch.build()).text
    }

    @Test
    fun `a raised small number after a word falls silent and an exponent is read`() {
        listOf(
            Triple("but never both.", "1", "but never both. Next"),
            Triple("as Smith,", "3", "as Smith, Next"),
            Triple("by Andreas Gal", "∗", "by Andreas Gal Next"),
            Triple("cited by the authors", "12,13", "cited by the authors Next"),
            Triple("थे लेकिन कभी नहीं", "2", "थे लेकिन कभी नहीं Next"),
            Triple("we take x", "2", "we take x2 Next"),
            Triple("about 1.0 × 10", "21", "about 1.0 × 1021 Next"),
            Triple("about 1.0 × 10−", "21", "about 1.0 × 10−21 Next"),
            Triple("a sky area of 600 deg", "2", "a sky area of 600 deg2 Next"),
            Triple("the statistic χ", "2", "the statistic χ2 Next"),
            Triple("the area (x+1)", "2", "the area (x+1)2 Next"),
        ).forEach { (before, marker, heard) ->
            val text =
                withLine(listOf(Triple(before, BODY, 0f), Triple(marker, SMALL, RISE), Triple(" Next", BODY, 0f)))
            assertTrue("$before^$marker: $text", heard in text.replace('\n', ' '))
        }
    }

    @Test
    fun `a raised digit beside a maths operator is an exponent, and a raised plus is a charge`() {
        listOf(
            listOf("so E = mc", "2", " holds") to "so E = mc2 holds",
            listOf("solve ax", "2", " + bx + c") to "solve ax2 + bx + c",
            listOf("where ds", "2", " = dt") to "where ds2 = dt",
            listOf("the ion Na", "+", " binds") to "the ion Na+ binds",
        ).forEach { (pieces, heard) ->
            val text = withLine(
                listOf(Triple(pieces[0], BODY, 0f), Triple(pieces[1], SMALL, RISE), Triple(pieces[2], BODY, 0f)),
            )
            assertTrue("$pieces: $text", heard in text.replace('\n', ' '))
        }
    }

    @Test
    fun `a subscript and a small word on the line are read`() {
        val subscript = withLine(listOf(Triple("the sentences B", BODY, 0f), Triple("1", SMALL, -2f)))
        assertTrue(subscript, "the sentences B1" in subscript)
        val ordinal =
            withLine(listOf(Triple("on the 2", BODY, 0f), Triple("nd", SMALL, RISE), Triple(" day", BODY, 0f)))
        assertTrue(ordinal, "on the 2nd day" in ordinal)
    }

    @Test
    fun `small lines at the foot of the page are footnotes, each with its number`() {
        val sketch = PageSketch()
        val below = sketch.lines(List(4) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        sketch.mixed(listOf(Triple("It ends here.", BODY, 0f), Triple("1", SMALL, RISE)), LEFT, below)
        val notes = below - PITCH * 2
        sketch.mixed(
            listOf(Triple("1", NOTE * 0.7f, 3f), Triple("Even if these seem common sense to you,", NOTE, 0f)),
            LEFT,
            notes,
        )
        sketch.line("they are disputed among philosophers.", LEFT, notes - NOTE * 1.2f, size = NOTE)
        sketch.mixed(
            listOf(Triple("2", NOTE * 0.7f, 3f), Triple("Well, there is one case.", NOTE, 0f)),
            LEFT,
            notes - NOTE * 2.4f,
        )
        val units = units(sketch)
        val footnotes = units.filter { it.kind == UnitKind.FOOTNOTE }
        assertEquals(
            listOf(
                1 to "Even if these seem common sense to you, they are disputed among philosophers.",
                2 to "Well, there is one case.",
            ),
            footnotes.map { it.noteRef to it.text },
        )
        assertTrue(units.first { it.text.startsWith("It ends") }.text == "It ends here.")
        assertTrue(units.filterNot { it.kind == UnitKind.FOOTNOTE }.all { it.kind == UnitKind.BODY })
    }

    @Test
    fun `a note carried on from the page before runs the width of the column`() {
        val sketch = PageSketch()
        val below = sketch.lines(List(4) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        val notes = below - PITCH
        sketch.line("we should allow sentences to be both true and false at the same time here.", LEFT, notes, NOTE)
        sketch.line("There are such systems of logic, but not in this book.", LEFT, notes - NOTE * 1.2f, NOTE)
        val units = units(sketch)
        assertEquals(listOf(null, null), units.filter { it.kind == UnitKind.FOOTNOTE }.map { it.noteRef })
        assertEquals(2, units.count { it.kind == UnitKind.FOOTNOTE })
    }

    @Test
    fun `small text that is not at the foot of its column, or a stray cell, stays body text`() {
        val stray = PageSketch()
        val below = stray.lines(List(4) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        stray.line("State", LEFT, below - PITCH, NOTE)
        assertTrue(units(stray).none { it.kind == UnitKind.FOOTNOTE })

        val quote = PageSketch()
        val after = quote.lines(List(4) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        quote.line("A long quotation set a little smaller than the body text of the page.", LEFT, after - PITCH, NOTE)
        quote.lines(List(3) { "Body text carries on below the quotation, line $it." }, LEFT, after - PITCH * 3)
        assertTrue(units(quote).none { it.kind == UnitKind.FOOTNOTE })
    }

    @Test
    fun `a paragraph opening with a note label is an aside`() {
        listOf(
            "Note: Bring your identity card to the office." to UnitKind.ASIDE,
            "NOTE – The fee is not refundable." to UnitKind.ASIDE,
            "टिप्पणी: यह शुल्क वापस नहीं होगा।" to UnitKind.ASIDE,
            "দ্রষ্টব্য: এই ফি ফেরত দেওয়া হবে না।" to UnitKind.ASIDE,
            "Note that the fee is not refundable." to UnitKind.BODY,
            "N.B. The fee is not refundable." to UnitKind.ASIDE,
            "Note - The fee is not refundable." to UnitKind.ASIDE,
            "नोट - यह शुल्क वापस नहीं होगा।" to UnitKind.ASIDE,
            "বিঃদ্রঃ এই ফি ফেরত দেওয়া হবে না।" to UnitKind.ASIDE,
            "बि:द्र: यह शुल्क वापस नहीं होगा।" to UnitKind.ASIDE,
            "বি:দ্র: এই ফি ফেরত দেওয়া হবে না।" to UnitKind.ASIDE,
            "नोट्स: यह शुल्क वापस नहीं होगा।" to UnitKind.ASIDE,
            "N.B.— The fee is not refundable." to UnitKind.ASIDE,
            "N.B., the fee is not refundable." to UnitKind.ASIDE,
            "Note\u00A0: The fee is not refundable." to UnitKind.ASIDE,
            // A word joined to "note" by a hyphen is not a label.
            "Note-taking helps you keep up in class." to UnitKind.BODY,
            "नोट-बुक में अपना नाम लिखें।" to UnitKind.BODY,
            "নোট-বই খুলে প্রথম পাতা পড়ো।" to UnitKind.BODY,
            "Notes.txt holds the list of files." to UnitKind.BODY,
        ).forEach { (text, kind) ->
            val sketch = PageSketch()
            val below = sketch.lines(List(3) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
            sketch.line(text, LEFT, below - PITCH)
            assertEquals(text, kind, units(sketch).last().kind)
        }
    }

    @Test
    fun `with markers not silenced the text keeps them`() {
        val config = LayoutConfig(notes = NoteConfig(silenceMarkers = false))
        val sketch = PageSketch()
        val below = sketch.lines(List(3) { "Body text set in the regular size of this page, line $it." }, LEFT, TOP)
        sketch.mixed(listOf(Triple("but never both.", BODY, 0f), Triple("1", SMALL, RISE)), LEFT, below)
        val text = ReadingUnitBuilder(layoutConfig = config).readingText(sketch.build()).text
        assertTrue(text, "never both.1" in text)
        assertFalse("never both.1" in builder.readingText(sketch.build()).text)
    }

    private companion object {
        const val LEFT = 50f
        const val TOP = 760f
        const val PITCH = BODY * 1.2f
        const val SMALL = BODY * 0.65f
        const val RISE = 4f
        const val NOTE = BODY * 0.8f
    }
}
