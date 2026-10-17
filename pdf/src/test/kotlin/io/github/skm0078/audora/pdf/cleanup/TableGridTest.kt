// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/TableGridTest.kt
// Purpose: Table grids on synthetic pages: wrapped cells, cells spanning rows, two-line headers, charts (RI-4a).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.layout.PageSketch
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class TableGridTest {
    private val builder = ReadingUnitBuilder()

    private class Case(val name: String, val draw: PageSketch.() -> Unit, val table: List<List<String>>)

    /** Each table sits between a paragraph above and one below, which must stay running text. */
    private val cases = listOf(
        Case(
            "a cell wrapped over two lines stays in its row",
            {
                row(TOP, "Name" to COL_A, "Role" to COL_B, "Years" to COL_C)
                row(TOP - PITCH, "Ann" to COL_A, "Pilot and" to COL_B, "4" to COL_C)
                row(TOP - 2 * PITCH, "navigator" to COL_B)
                row(TOP - 3 * PITCH, "Bob" to COL_A, "Cook" to COL_B, "7" to COL_C)
                row(TOP - 4 * PITCH, "Cy" to COL_A, "Guard" to COL_B, "2" to COL_C)
            },
            listOf(
                listOf("Name", "Role", "Years"),
                listOf("Ann", "Pilot and navigator", "4"),
                listOf("Bob", "Cook", "7"),
                listOf("Cy", "Guard", "2"),
            ),
        ),
        Case(
            "a rank spanning two rows goes with the first, and the rows stay apart",
            {
                row(TOP, "Rank" to COL_A, "Roll" to COL_B, "Name" to COL_C, "GPA" to COL_D)
                row(TOP - 1.5f * PITCH, "1021" to COL_B, "Ann Roy" to COL_C, "4.00" to COL_D)
                row(TOP - 2.25f * PITCH, "1st" to COL_A)
                row(TOP - 3f * PITCH, "1171" to COL_B, "Bob Das" to COL_C, "4.00" to COL_D)
                row(TOP - 4.5f * PITCH, "2nd" to COL_A, "1037" to COL_B, "Cy Sen" to COL_C, "3.91" to COL_D)
            },
            listOf(
                listOf("Rank", "Roll", "Name", "GPA"),
                listOf("1st", "1021", "Ann Roy", "4.00"),
                listOf("", "1171", "Bob Das", "4.00"),
                listOf("2nd", "1037", "Cy Sen", "3.91"),
            ),
        ),
        Case(
            "a header over two lines is one header row",
            {
                row(TOP, "State /" to COL_A, "Funds" to COL_B, "Spent" to COL_C)
                row(TOP - PITCH, "Territory" to COL_A)
                row(TOP - 2 * PITCH, "Assam" to COL_A, "45.99" to COL_B, "51.31" to COL_C)
                row(TOP - 3 * PITCH, "Goa" to COL_A, "53.52" to COL_B, "75.06" to COL_C)
                row(TOP - 4 * PITCH, "Bihar" to COL_A, "134.52" to COL_B, "0.00" to COL_C)
            },
            listOf(
                listOf("State / Territory", "Funds", "Spent"),
                listOf("Assam", "45.99", "51.31"),
                listOf("Goa", "53.52", "75.06"),
                listOf("Bihar", "134.52", "0.00"),
            ),
        ),
        Case(
            "two columns set closer than a cell gap are split where the gap runs down them",
            {
                row(TOP, "#" to COL_A, "State" to CLOSE, "Funds" to COL_B, "Spent" to COL_C)
                row(TOP - PITCH, "1" to COL_A, "Assam" to CLOSE, "45.99" to COL_B, "51.31" to COL_C)
                row(TOP - 2 * PITCH, "2" to COL_A, "Goa" to CLOSE, "53.52" to COL_B, "75.06" to COL_C)
                row(TOP - 3 * PITCH, "3" to COL_A, "Bihar" to CLOSE, "134.52" to COL_B, "0.00" to COL_C)
                row(TOP - 4 * PITCH, "Total" to CLOSE, "233.03" to COL_B, "126.37" to COL_C)
            },
            listOf(
                listOf("#", "State", "Funds", "Spent"),
                listOf("1", "Assam", "45.99", "51.31"),
                listOf("2", "Goa", "53.52", "75.06"),
                listOf("3", "Bihar", "134.52", "0.00"),
                listOf("", "Total", "233.03", "126.37"),
            ),
        ),
        Case(
            "a chart's rotated axis title, set letter by letter, is not a table",
            {
                for ((i, letters) in listOf("n 2 a", "a 1 e", "e 0 M", "M -1 n").withIndex()) {
                    val (a, b, c) = letters.split(' ')
                    row(TOP - i * PITCH, a to COL_A, b to COL_B, c to COL_C)
                }
            },
            emptyList(),
        ),
    )

    @Test
    fun `tables are read into rows of cells`() {
        for (case in cases) {
            val sketch = PageSketch()
            sketch.lines(listOf("The paragraph above the table."), COL_A, TOP + 3 * PITCH)
            case.draw(sketch)
            sketch.lines(listOf("The paragraph below the table."), COL_A, TOP - 8 * PITCH)
            val units = builder.build(sketch.build())
            val running = units.filterNot { it.kind.isTable }.joinToString(" ") { it.text }
            assertEquals(case.name, "The paragraph above the table. The paragraph below the table.", running)
            assertEquals(case.name, case.table, units.filter { it.kind.isTable }.map(::cells))
        }
    }

    private fun cells(unit: ReadingUnit): List<String> =
        unit.table?.cells.orEmpty().map { unit.text.substring(it.start, it.end) }

    private fun PageSketch.row(top: Float, vararg cells: Pair<String, Float>) {
        for ((text, left) in cells) line(text, left, top)
    }

    private companion object {
        const val TOP = 600f
        const val PITCH = 12f
        const val COL_A = 72f

        /** 0.7 em right of a one-character cell at [COL_A]: closer than a cell gap, wider than a word space. */
        const val CLOSE = 84f
        const val COL_B = 160f
        const val COL_C = 250f
        const val COL_D = 340f
    }
}
