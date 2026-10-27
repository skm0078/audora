// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReadingFontsTest.kt
// Purpose: Every text view font choice has its own typeface, the two bundled ones carry real bold files, and the
//          two older choices still use the phone's serif and sans (CF-5).

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFontsTest {
    @Test
    fun `each font choice has a typeface of its own`() {
        val families = ReadingFont.entries.map(ReadingFonts::familyOf)
        assertEquals(ReadingFont.entries.size, families.toSet().size)
    }

    @Test
    fun `the book and clean choices still use the phone's own faces`() {
        assertEquals(FontFamily.Serif, ReadingFonts.familyOf(ReadingFont.SERIF))
        assertEquals(FontFamily.SansSerif, ReadingFonts.familyOf(ReadingFont.SANS))
    }

    @Test
    fun `the bundled fonts have regular and bold, so bold word starts are not faked`() {
        listOf(ReadingFont.ATKINSON_HYPERLEGIBLE, ReadingFont.OPEN_DYSLEXIC).forEach { font ->
            val family = ReadingFonts.familyOf(font)
            assertTrue("$font is bundled", family is FontListFontFamily)
            val weights = (family as FontListFontFamily).fonts.map { it.weight }.toSet()
            assertEquals("weights of $font", setOf(FontWeight.Normal, FontWeight.Bold), weights)
        }
    }

    @Test
    fun `a saved choice is found again by name`() {
        ReadingFont.entries.forEach { font ->
            assertEquals(font, ReadingFont.entries.firstOrNull { it.name == font.name })
        }
        val names = ReadingFont.entries.map { it.name }.toSet()
        assertEquals("names are stored, so they must stay unique", ReadingFont.entries.size, names.size)
    }
}
