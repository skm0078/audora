// File: core/src/test/kotlin/io/github/skm0078/audora/core/text/UrlTextTest.kt
// Purpose: URL-escaped settings text round-trips in every script we read, and damaged text reads as missing.

package io.github.skm0078.audora.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlTextTest {
    @Test
    fun `text in every script survives a round trip`() {
        val samples = listOf("", "plain", "a b;c=d\te%f", "हिन्दी पाठ", "বাংলা লেখা", "EN=SHERPA/name;HI=SYSTEM/x")
        for (text in samples) assertEquals(text, UrlText.decode(UrlText.encode(text)))
    }

    @Test
    fun `escaped text has no separators left in it`() {
        val escaped = UrlText.encode("a;b=c\td\ne")
        assertEquals(false, escaped.any { it in ";=\t\n" })
    }

    @Test
    fun `a stray percent sign reads as missing, not a crash`() {
        assertNull(UrlText.decode("50%"))
        assertNull(UrlText.decode("%zz"))
    }
}
