// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/PrintSlugsTest.kt
// Purpose: What a printer's slug looks like: layout file names and print times, and what is not one.

package io.github.skm0078.audora.pdf.cleanup

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintSlugsTest {
    @Test
    fun `lines ending with a layout file's name and bare stamps to the second are slugs by themselves`() {
        val slugs = listOf(
            "HUD3_25_09Single-Bengali-BE.indd Sec1:9",
            "Chapter 9 Supporting Children’s Transitions.indd 71",
            "ch04.qxd 2/3/09 10:12 AM Page 62",
            "Cover 2 and 3.indd All Pages",
            "17-12-2019 12:54:06",
            "22-Nov-2019 2:51:06 PM",
            "Cover 2 and 3.indd All Pages 22-Nov-2019 2:51:06 PM",
            "HUD3_25_09Single-Bengali-BE.indd UD3_25_09Single-Bengali-BE.indd Sec1:6 Sec1:6",
            "0_Prelims.indd 8 21-12-2019 12:00:11",
            "HUD3_25_09Single-Bengali-BE.indd UD3_25_09Single-Bengali-BE.indd 9 9 5/13/11 /13/11 6:45 AM",
        )
        for (line in slugs) {
            assertEquals(line, true, PrintSlugs.standsAlone(line))
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
            // A file named in a sentence, a receipt's or a log's stamp with words around it, a no-break space.
            "Open the file Lesson01.indd and click Save",
            "Paid on 17-12-2019 12:54:06 by card",
            "17-12-2019 12:54:06 ERROR disk full",
            "17-12-2019\u00A012:54:06",
            "",
        )
        for (line in lines) {
            assertEquals(line, false, PrintSlugs.standsAlone(line))
        }
    }

    @Test
    fun `print times are written alike to the minute`() {
        assertEquals(setOf("5/13/11 6:45"), PrintSlugs.times("5/13/11, 6:45 AM"))
        assertEquals(setOf("5/13/11 6:45"), PrintSlugs.times("x 5/13/11  6:45:59 y"))
        assertEquals(setOf("22-Nov-2019 2:51"), PrintSlugs.times("22-Nov-2019 2:51:06 PM"))
        assertEquals(setOf("5/13/11 6:45"), PrintSlugs.times("5/13/11 /13/11 6:45 AM 6:45 AM"))
        assertEquals(emptySet<String>(), PrintSlugs.times("Chemistry 16/03/2024"))
    }
}
