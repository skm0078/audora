// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/PrintSlugsTest.kt
// Purpose: What a printer's slug looks like: layout file names and print times, and what is not one.

package io.github.skm0078.audora.pdf.cleanup

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintSlugsTest {
    @Test
    fun `layout file names and print times with seconds are slugs by themselves`() {
        val slugs = listOf(
            "HUD3_25_09Single-Bengali-BE.indd Sec1:9",
            "Chapter 9 Supporting Children’s Transitions.indd 71",
            "ch04.qxd 2/3/09 10:12 AM Page 62",
            "Cover 2 and 3.indd All Pages",
            "17-12-2019 12:54:06",
            "22-Nov-2019 2:51:06 PM",
        )
        for (line in slugs) {
            assertEquals(line, true, PrintSlugs.namesLayoutFile(line) || PrintSlugs.hasStampWithSeconds(line))
        }
    }

    @Test
    fun `dates, times to the minute and words like file names are not slugs by themselves`() {
        val lines = listOf(
            "5/13/11 6:45 AM",
            "Physics 15/03/2024 10:30",
            "दिनांक १२.०५.२०१९, १०:३० बजे",
            "Save it as index.html",
            "The inddex is wrong",
            "Meeting at 10:30:00 hours",
            "",
        )
        for (line in lines) {
            assertEquals(line, false, PrintSlugs.namesLayoutFile(line) || PrintSlugs.hasStampWithSeconds(line))
        }
    }

    @Test
    fun `print times are written alike to the minute`() {
        assertEquals(setOf("5/13/11 6:45"), PrintSlugs.times("5/13/11, 6:45 AM"))
        assertEquals(setOf("5/13/11 6:45"), PrintSlugs.times("x 5/13/11  6:45:59 y"))
        assertEquals(setOf("22-Nov-2019 2:51"), PrintSlugs.times("22-Nov-2019 2:51:06 PM"))
        assertEquals(emptySet<String>(), PrintSlugs.times("Chemistry 16/03/2024"))
    }
}
