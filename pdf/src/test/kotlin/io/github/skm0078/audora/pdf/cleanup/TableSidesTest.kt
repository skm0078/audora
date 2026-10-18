// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/TableSidesTest.kt
// Purpose: The one rule the table finder and grid share for two tables side by side versus one wide table.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSidesTest {
    private val config = TableConfig()

    /** Rows written "left cells | right cells", cells split on two spaces; an empty side is blank. */
    private fun rows(vararg text: String): List<Pair<List<String>, List<String>>> = text.map { row ->
        val (left, right) = row.split("|").map { side ->
            side.trim().split("  ").map(String::trim).filter(String::isNotEmpty)
        }
        left to right
    }

    @Test
    fun `two tables only when they show it, else one`() {
        val cases = listOf(
            "the same header" to rows("No.  Name | No.  Name", "1  Asha | 6  Ravi", "2  Mina | 7  Sita") to true,
            "a serial number opening the right side" to
                rows("Ward  Head | No.  Marks", "A  Asha | 1  37", "B  Mina | 2  74", "C  Ravi | 3  41") to true,
            "labels both sides, same length (a directory)" to
                rows("Name  Designation | Phone  Remarks", "Asha  Clerk | 2231  on leave", "Mina  Typist | 2232  —") to
                false,
            "figures under a header" to
                rows("State  Unit | 2019  2020", "Goa  t | 37  41", "Assam  t | 74  –", "Bihar  t | 111  123") to false,
            "figures, a label row left alone with its figures blank" to
                rows("State  Unit | 2019  2020", "Goa  t | 37  41", "North  East |", "Assam  t | 74  82") to false,
            "figures with rows on the right only (a right table running longer)" to
                rows(
                    "Ward  Head | Code  Roll",
                    "A  Asha | 37  4105",
                    "B  Mina | 74  4109",
                    "| 41  4113",
                    "| 52  4120",
                ) to
                true,
            "a right table starting lower" to
                rows("Ward  Head | Site  Head", "A  Asha |", "B  Mina | Rly  Ravi", "C  Joy | Bus  Sita") to true,
            "a section row on one side" to
                rows("Item  Unit | 2019  2020", "A.  Revenue |", "Tax  t | 37  41", "Fees  t | 74  82") to false,
        )
        for ((case, two) in cases) assertEquals(case.first, two, TableSides.twoTables(case.second, config))
    }

    @Test
    fun `figures written as accounts and Indic tables write them are figures`() {
        val forms =
            listOf(
                "(1,234)", "₹12", "₹ 12", "Rs. 12", "Rs.12", "500/-", "12*", "12 %", "৩.৯১", "निरंक", "शून्य",
                "শূন্য", "নেই", "N.A.", "–", "RS. 12", "rs.12", "(-1,234)", "(₹ -1,234)",
            )
        for (form in forms) {
            val side = listOf(listOf("2019", "2020"), listOf(form, "41"), listOf("74", form))
            assertTrue(form, TableSides.readsAsFigures(side, config.maxHeaderRows, config.minFigureRows))
        }
        // Only plain spaces: a no-break space inside a figure is a word gap on every platform. The JDK's \s has no
        // no-break space, so this passes here either way; only Android's ICU regex would take one for `\s`.
        for (form in listOf("Rs.\u00A012", "12\u00A0%")) {
            assertFalse(form, NUMBER_CELL.matches(form))
        }
    }
}
