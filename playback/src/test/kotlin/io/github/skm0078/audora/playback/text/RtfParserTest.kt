// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/RtfParserTest.kt
// Purpose: An RTF file's text, Unicode and code-page characters come out; tables of fonts and styles do not.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Test

class RtfParserTest {
    /** RTF's `\u` control word, kept out of the source so the compiler does not read it as an escape. */
    private val unicodeWord = "\\u"

    @Test
    fun `reads paragraphs, code page bytes, Unicode and the title, skipping tables and fields`() {
        val source = """{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0 Times New Roman;}}{\colortbl;\red0\green0\blue0;}
            {\*\generator Riched20;}{\info{\title Two Songs}{\author Someone}}
            \pard\b Chapter 1\b0\par
            Caf\'e9 \ldblquote quoted\rdblquote  and ${unicodeWord}2350?${unicodeWord}2381?${unicodeWord}2325? Hindi.
            {\field{\*\fldinst PAGE}{\fldrslt 7}}\par
            {\pict\pngblip 89504e47}Line one\line line two.\par
            \trowd A\cell B\cell\row
            Text{\super\chftn}{\footnote\pard\plain {\super\chftn} A footnote.}\par}"""
        val rtf = RtfParser.parse(source)
        assertEquals("Two Songs", rtf.title)
        assertEquals(listOf("A footnote."), rtf.notes)
        val paragraphs = rtf.text.split("\n\n").map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            listOf("Chapter 1", "Café “quoted” and म्क Hindi. 7", "Line one line two.", "A; B;", "Text"),
            paragraphs,
        )
    }

    @Test
    fun `Devanagari from Unicode escapes, with the fallback bytes dropped`() {
        val rtf = RtfParser.parse("""{\rtf1\uc1 ${unicodeWord}2350\'3f${unicodeWord}2344\'3f\par}""")
        assertEquals("मन", rtf.text.trim())
    }
}
