// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/extract/PdfTextStringTest.kt
// Purpose: PDF text string decoding for /ActualText values.

package io.github.skm0078.audora.pdf.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextStringTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `table of encodings`() {
        val cases = listOf(
            bytes(0xFE, 0xFF, 0x09, 0x2E, 0x09, 0x3E) to "मा",
            bytes(0xEF, 0xBB, 0xBF, 0xE0, 0xA6, 0xAE) to "ম",
            bytes(0x80, 0x20, 0x41) to "\u2022 A",
            bytes(0xE9, 0x93) to "\u00E9\uFB01",
            bytes(0xFE, 0xFF) to "",
            bytes() to "",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, PdfTextString.decode(input)) }
    }
}
