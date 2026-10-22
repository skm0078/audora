// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/UnitReadingTest.kt
// Purpose: Which units each reading mode plays and what the voice says for them (RI-2b, RI-4b, RI-5b, RI-7b).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.CaptionMode
import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.EquationMode
import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.TermSpan
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.book.PageCursor
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.normalize.TextRole
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitReadingTest {
    private fun body(index: Int, score: Float? = null) = unit(0, index).copy(speakScore = score)

    private fun kind(index: Int, kind: UnitKind, text: String = "Text $index.", lang: Lang = Lang.EN) =
        unit(0, index, lang).copy(kind = kind, text = text)

    private fun table(index: Int, row: Int?, text: String = "Ram 34", lang: Lang = Lang.EN): ReadingUnit {
        val gap = text.indexOf(' ')
        val cells = listOf(CellSpan(0, gap), CellSpan(gap + 1, text.length))
        val info = TableRowInfo(0, row, 2, 2, listOf("Name", "Age:"), cells)
        val kind = if (row == null) UnitKind.TABLE_SUMMARY else UnitKind.TABLE_ROW
        return unit(0, index, lang).copy(kind = kind, text = text, table = info)
    }

    private fun played(modes: ReadingModes, units: List<ReadingUnit>) = UnitReading(modes).plays(units).map { it.index }

    @Test
    fun `a run of unreadable units plays one cue, and the switch reads them all`() {
        val units = listOf(body(0, 0.9f), body(1, 0.2f), body(2, 0.1f), body(3, 0.54f), body(4, 0.55f), body(5, null))
        val reading = UnitReading(ReadingModes())
        assertEquals(listOf(0, 1, 4, 5), reading.plays(units).map { it.index })
        assertEquals(Speech.Cue, reading.speechOf(units[1]))
        assertEquals(3, reading.unreadableCount(units))
        assertEquals(Speech.Text("Sentence 4 of page 0.", TextRole.BODY), reading.speechOf(units[4]))
        val off = UnitReading(ReadingModes(skipUnreadable = false))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), off.plays(units).map { it.index })
        assertEquals(Speech.Text("Sentence 1 of page 0.", TextRole.BODY), off.speechOf(units[1]))
    }

    @Test
    fun `tables and equations are never judged as junk`() {
        val row = table(0, 0).copy(speakScore = 0.1f)
        val equation = kind(1, UnitKind.EQUATION, "x = y (1)").copy(speakScore = 0.1f)
        val reading = UnitReading(ReadingModes(tables = TableMode.ROWS))
        assertEquals(listOf(0, 1), reading.plays(listOf(row, equation)).map { it.index })
        assertEquals(Speech.Text("Row 1. Name: Ram. Age: 34.", TextRole.BODY), reading.speechOf(row))
    }

    @Test
    fun `resuming inside a skipped run starts at the next unit played`() {
        val units = listOf(body(0), body(1, 0.1f), body(2, 0.1f), body(3))
        val cursor = PageCursor(pageCount = 1, startPage = 0, startChar = units[2].charStart)
        val playable = cursor.playable(0, units)
        assertEquals("the run's second unit plays the cue", listOf(2, 3), played(ReadingModes(), playable))
        val afterRun = PageCursor(pageCount = 1, startPage = 0, startChar = units[2].charEnd)
        assertEquals(listOf(3), played(ReadingModes(), afterRun.playable(0, units)))
    }

    @Test
    fun `each table mode`() {
        val units = listOf(body(0), table(1, null, "Name Age"), table(2, 0), table(3, 1, "Sita 29"), body(4))
        assertEquals(listOf(0, 1, 4), played(ReadingModes(tables = TableMode.SUMMARY), units))
        assertEquals(listOf(0, 1, 2, 3, 4), played(ReadingModes(tables = TableMode.ROWS), units))
        assertEquals(listOf(0, 4), played(ReadingModes(tables = TableMode.SKIP), units))
        val reading = UnitReading(ReadingModes(tables = TableMode.ROWS))
        assertEquals(Speech.Text("2 rows, 2 columns.", TextRole.BODY), reading.speechOf(units[1]))
        assertEquals(Speech.Text("Row 2. Name: Sita. Age: 29.", TextRole.BODY), reading.speechOf(units[3]))
    }

    @Test
    fun `a continued table says it continues before its rows, in summary and in row by row`() {
        val carried = { unit: ReadingUnit -> unit.copy(table = unit.table!!.copy(continued = true, rowsBefore = 12)) }
        val units = listOf(table(0, null, "Name Age"), table(1, 0), table(2, 1, "Sita 29")).map(carried)
        val continues = Speech.Text("Table continues, 2 more rows.", TextRole.BODY)

        assertEquals(listOf(0), played(ReadingModes(tables = TableMode.SUMMARY), units))
        assertEquals(continues, UnitReading(ReadingModes(tables = TableMode.SUMMARY)).speechOf(units[0]))
        val rows = UnitReading(ReadingModes(tables = TableMode.ROWS))
        assertEquals(listOf(0, 1, 2), rows.plays(units).map { it.index })
        assertEquals(
            listOf(continues, Speech.Text("Row 13. Name: Ram. Age: 34.", TextRole.BODY)),
            units.take(2).map(rows::speechOf),
        )
    }

    @Test
    fun `footnotes read where printed or skipped`() {
        val units = listOf(body(0), kind(1, UnitKind.FOOTNOTE))
        assertEquals(listOf(0, 1), played(ReadingModes(), units))
        assertEquals(listOf(0), played(ReadingModes(footnotes = FootnoteMode.SKIP), units))
    }

    @Test
    fun `an equation run is announced once by its number, read as written, or skipped`() {
        val units = listOf(
            body(0),
            kind(1, UnitKind.EQUATION, "f(x) = ∑ a"),
            kind(2, UnitKind.EQUATION, "+ b (2.14)"),
            body(3),
            kind(4, UnitKind.EQUATION, "y ≤ 3"),
            kind(5, UnitKind.EQUATION, "समीकरण (४)", Lang.HI),
        )
        val announce = UnitReading(ReadingModes())
        assertEquals(listOf(0, 2, 3, 5), announce.plays(units).map { it.index })
        assertEquals(Speech.Text("Equation 2.14.", TextRole.BODY), announce.speechOf(units[2]))
        assertEquals(Speech.Text("Equation.", TextRole.BODY), announce.speechOf(units[4]))
        assertEquals(Speech.Text("समीकरण ४।", TextRole.BODY), announce.speechOf(units[5]))
        val written = UnitReading(ReadingModes(equations = EquationMode.AS_WRITTEN))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), written.plays(units).map { it.index })
        assertEquals(Speech.Text("y ≤ 3", TextRole.EQUATION), written.speechOf(units[4]))
        assertEquals(listOf(0, 3), played(ReadingModes(equations = EquationMode.SKIP), units))
    }

    @Test
    fun `a caption's label gets a cue, its other sentences do not, and Skip leaves it out`() {
        val label = kind(1, UnitKind.CAPTION, "Fig. 2.")
        val rest = kind(2, UnitKind.CAPTION, "Dose maps for each patient.")
        val reading = UnitReading(ReadingModes())
        assertEquals(Speech.Text("Fig. 2.", TextRole.BODY, cueBefore = true), reading.speechOf(label))
        assertEquals(Speech.Text("Dose maps for each patient.", TextRole.BODY), reading.speechOf(rest))
        for (text in listOf("Table IV: results", "चित्र 4", "সারণি ২ ।", "Figure 10")) {
            assertEquals(text, true, (reading.speechOf(kind(1, UnitKind.CAPTION, text)) as Speech.Text).cueBefore)
        }
        val units = listOf(body(0), label, rest, body(3))
        assertEquals(listOf(0, 3), played(ReadingModes(captions = CaptionMode.SKIP), units))
    }

    @Test
    fun `a heading opening a chapter page gets a cue`() {
        val heading = kind(0, UnitKind.HEADING, "Chapter 2")
        val later = kind(3, UnitKind.HEADING, "A section")
        val reading = UnitReading(ReadingModes(), chapterPages = setOf(0))
        assertEquals(Speech.Text("Chapter 2", TextRole.HEADING, cueBefore = true), reading.speechOf(heading))
        assertEquals(Speech.Text("A section", TextRole.HEADING), reading.speechOf(later))
        assertEquals(Speech.Text("Chapter 2", TextRole.HEADING), UnitReading(ReadingModes()).speechOf(heading))
    }

    @Test
    fun `skip switches trim the text, drop a unit left empty and never touch tables`() {
        val modes = ReadingModes(skipParentheses = true, skipBrackets = true, skipLinks = true)
        val units = listOf(
            kind(0, UnitKind.BODY, "The river (see map) rose [3] at www.x.org."),
            kind(1, UnitKind.ASIDE, "(Photo courtesy of the archive.)"),
            table(2, row = null, text = "Name (yrs)"),
        )
        val reading = UnitReading(modes)

        assertEquals(listOf(0, 2), reading.plays(units).map { it.index })
        assertEquals("The river rose at.", (reading.speechOf(units[0]) as Speech.Text).text)
        assertEquals(listOf(0, 1, 2), played(ReadingModes(), units))
    }

    @Test
    fun `page numbers are said before a page's first unit only`() {
        val reading = UnitReading(ReadingModes(announcePages = true))

        assertEquals("Page 1. Text 0.", (reading.speechOf(kind(0, UnitKind.BODY)) as Speech.Text).text)
        assertEquals("Text 1.", (reading.speechOf(kind(1, UnitKind.BODY)) as Speech.Text).text)
        assertEquals(
            "पृष्ठ 1। Text 0.",
            (reading.speechOf(kind(0, UnitKind.HEADING, lang = Lang.HI)) as Speech.Text).text,
        )
        assertEquals("Text 0.", (UnitReading(ReadingModes()).speechOf(kind(0, UnitKind.BODY)) as Speech.Text).text)
    }

    @Test
    fun `skim reads headings and each paragraph's first sentence`() {
        val end = 400
        val units = listOf(
            kind(0, UnitKind.HEADING).copy(pauseAfterMs = 600),
            kind(1, UnitKind.BODY),
            kind(2, UnitKind.BODY).copy(pauseAfterMs = end),
            kind(3, UnitKind.BODY),
            kind(4, UnitKind.BODY).copy(pauseAfterMs = end),
            kind(5, UnitKind.LIST_ITEM).copy(pauseAfterMs = 300),
            kind(6, UnitKind.LIST_ITEM),
            kind(7, UnitKind.LIST_ITEM).copy(pauseAfterMs = 300),
            table(8, row = null),
            table(9, row = 0),
            kind(10, UnitKind.FOOTNOTE),
            kind(11, UnitKind.CAPTION, text = "Fig. 2. A cell."),
        )

        assertEquals(listOf(0, 1, 3, 5, 6, 8, 11), played(ReadingModes(skim = true), units))
        assertEquals(units.indices.toList(), played(ReadingModes(tables = TableMode.ROWS), units))
    }

    @Test
    fun `skim also reads a sentence with a bold term, whole, and a run-in bold heading`() {
        // All four are one paragraph: only the first would be skimmed without bold.
        val term = kind(1, UnitKind.BODY, text = "An enzyme is a protein.").copy(boldTerms = listOf(TermSpan(3, 9)))
        val runIn = kind(3, UnitKind.BODY, text = "Note this.").copy(boldTerms = listOf(TermSpan(0, 9)))
        val units = listOf(kind(0, UnitKind.BODY), term, kind(2, UnitKind.BODY), runIn)
        val skim = ReadingModes(skim = true)

        assertEquals(listOf(0, 1, 3), played(skim, units))
        assertEquals("An enzyme is a protein.", (UnitReading(skim).speechOf(term) as Speech.Text).text)
        assertEquals(listOf(0, 1, 2, 3), played(ReadingModes(), units))
    }

    @Test
    fun `skim still follows the other modes`() {
        val units = listOf(kind(0, UnitKind.BODY), table(1, row = null), kind(2, UnitKind.CAPTION, text = "Fig. 1."))

        val modes = ReadingModes(skim = true, tables = TableMode.SKIP, captions = CaptionMode.SKIP)

        assertEquals(listOf(0), played(modes, units))
    }
}
