// File: app/src/test/kotlin/io/github/skm0078/audora/ui/share/CardLookForReadingTest.kt
// Purpose: A quote card opens in the reader's page colours: sepia stays sepia, dark is night, the book's own colours
//          follow the app's light or dark theme.

package io.github.skm0078.audora.ui.share

import io.github.skm0078.audora.ui.reader.PageTone
import org.junit.Assert.assertEquals
import org.junit.Test

class CardLookForReadingTest {
    @Test
    fun `each page tone opens its matching card look`() {
        assertEquals(CardLook.SEPIA, CardLook.forReading(PageTone.SEPIA, darkApp = false))
        assertEquals(CardLook.NIGHT, CardLook.forReading(PageTone.DARK, darkApp = false))
        assertEquals(CardLook.PAPER, CardLook.forReading(PageTone.ORIGINAL, darkApp = false))
        assertEquals(CardLook.NIGHT, CardLook.forReading(PageTone.ORIGINAL, darkApp = true))
    }
}
