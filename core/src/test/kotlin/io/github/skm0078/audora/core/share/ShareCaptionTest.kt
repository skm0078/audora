// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/ShareCaptionTest.kt
// Purpose: Every share's words: the lines in quotes, cut to the text limit; the credit; then the invitation and link.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.config.ShareLinkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCaptionTest {
    private val link = ShareLinkConfig().appLink

    @Test
    fun `lines come in quotes, then the credit, then the invitation with the link last`() {
        val caption = ShareCaption.of("It was a dark night.", "The Storm, R. Das, page 4", "Listen free:", link)

        assertEquals("“It was a dark night.”\n— The Storm, R. Das, page 4\n\nListen free: $link", caption)
    }

    @Test
    fun `a picture's caption has no lines, only the credit and the link`() {
        val caption = ShareCaption.of(null, "The Storm, page 4", "Listen free:", link)

        assertEquals("— The Storm, page 4\n\nListen free: $link", caption)
    }

    @Test
    fun `with neither lines nor credit only the invitation and link remain`() {
        assertEquals("Listen free: $link", ShareCaption.of(" ", null, "Listen free: ", link))
    }

    @Test
    fun `long lines are cut to the text limit, never the whole page`() {
        val config = ShareCardConfig(maxWords = 3, maxTextWords = 5)
        val lines = (1..40).joinToString(" ") { "word$it" }

        val caption = ShareCaption.of(lines, "Book", "Listen:", link, config)

        assertTrue(caption.startsWith("“word1 word2 word3 word4 word5…”"))
        assertFalse(caption.contains("word6"))
    }

    @Test
    fun `Hindi and Bengali lines keep their own sentence ends`() {
        val caption = ShareCaption.of("वह घर गया। फिर सो गया।", "किताब", "सुनें:", link)

        assertTrue(caption.startsWith("“वह घर गया। फिर सो गया।”"))
    }
}
