// File: app/src/test/kotlin/io/github/skm0078/audora/ui/share/CardPicturesTest.kt
// Purpose: A picked picture is decoded no bigger than a Story card's long side, keeping its shape.

package io.github.skm0078.audora.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPicturesTest {
    @Test
    fun `a big phone photo comes down to 1920 on its long side, keeping its shape`() {
        assertEquals(1920 to 1440, CardPictures.cappedSize(4000, 3000))
        assertEquals(1080 to 1920, CardPictures.cappedSize(3024, 5376))
    }

    @Test
    fun `a picture already small enough is decoded as it is`() {
        assertNull(CardPictures.cappedSize(1920, 1080))
        assertNull(CardPictures.cappedSize(800, 600))
    }

    @Test
    fun `a very thin picture keeps at least one pixel`() {
        assertEquals(1920 to 1, CardPictures.cappedSize(40_000, 2))
    }
}
