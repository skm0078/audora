// File: app/src/test/kotlin/io/github/skm0078/audora/ui/shell/ReadableWidthTest.kt
// Purpose: Lists keep their phone padding on phones and a centred readable column on wide windows.

package io.github.skm0078.audora.ui.shell

import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.ui.theme.WindowWidths
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableWidthTest {
    @Test
    fun phonesKeepTheirPadding() {
        assertEquals(16.dp, readableSidePadding(411.dp, min = 16.dp))
        assertEquals(0.dp, readableSidePadding(360.dp))
    }

    @Test
    fun wideWindowsCentreTheColumn() {
        val width = 1_000.dp
        val side = readableSidePadding(width, min = 16.dp)
        assertEquals(180.dp, side)
        assertEquals(WindowWidths.READABLE, width - side * 2)
    }
}
