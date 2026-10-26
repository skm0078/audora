// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/WebAddressTest.kt
// Purpose: Typed and shared addresses become one https address; anything that is not a web page is refused.

package io.github.skm0078.audora.playback.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAddressTest {
    @Test
    fun `typed addresses become https`() {
        mapOf(
            "https://example.org/story" to "https://example.org/story",
            "  example.org/story  " to "https://example.org/story",
            "http://example.org/story?id=4" to "https://example.org/story?id=4",
            "HTTPS://Example.org/a" to "HTTPS://Example.org/a",
            "www.example.org" to "https://www.example.org",
            "example.org:8443/a" to "https://example.org:8443/a",
        ).forEach { (typed, expected) -> assertEquals(typed, expected, WebAddress.normalize(typed)) }
    }

    @Test
    fun `what is not a web page is refused`() {
        listOf(
            "",
            "   ",
            "hello",
            "file:///sdcard/a.html",
            "ftp://example.org/a",
            "javascript:alert(1)",
            "content://media/1",
            "mailto:someone@example.org",
            "https://user:secret@example.org/",
            "two words.org",
            "https://localhost/a",
            "https://.org/",
        ).forEach { assertNull(it, WebAddress.normalize(it)) }
    }

    @Test
    fun `the first link in shared text is found without the punctuation after it`() {
        assertEquals(
            "https://example.org/a?b=1",
            WebAddress.firstIn("Read this: https://example.org/a?b=1."),
        )
        assertEquals("https://example.org/x", WebAddress.firstIn("Title\nhttp://example.org/x)"))
        assertNull(WebAddress.firstIn("no link here"))
    }

    @Test
    fun `only the host is shown and logged`() {
        assertEquals("example.org", WebAddress.hostOf("https://www.example.org/private?token=abc"))
        assertEquals("", WebAddress.hostOf("not an address"))
    }
}
