// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/text/MarkdownParserDeviceTest.kt
// Purpose: The Markdown and text-book patterns compile on Android's own regex engine (ICU), not only the desktop JVM's.

package io.github.skm0078.audora.playback.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownParserDeviceTest {
    /** Phone test 2026-10-26: "Try a sample" crashed because one pattern failed to compile on Android only. */
    @Test
    fun parsesOnTheDevice() {
        val document = MarkdownParser.parse("# Welcome {#top}\n\nSome **bold** text and a [link](https://example.com).")
        assertFalse(document.isEmpty)
        val text = document.chapters.joinToString(" ") { "${it.title} ${it.blocks}" }
        assertFalse("Heading id left in: $text", "{#top}" in text)
        assertTrue(MarkdownParser.looksLikeMarkdown("# Title\n\n**bold**"))
    }
}
