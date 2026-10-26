// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/WebArticleExtractorTest.kt
// Purpose: The article is kept as titled paragraphs; menus, scripts and footers are not read; empty pages are refused.

package io.github.skm0078.audora.playback.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticleExtractorTest {
    private val extractor = WebArticleExtractor()

    @Test
    fun `the article's paragraphs are kept apart and the page around them is left out`() {
        val article = extractor.extract(page(paragraphs = 8), "https://news.example.org/river")

        requireNotNull(article)
        assertEquals("The River Story - Example News", article.title)
        val paragraphs = article.text.split("\n\n")
        assertTrue(paragraphs.size >= 8)
        assertTrue(paragraphs.any { it.startsWith("Paragraph 0 of the story.") })
        listOf("Home", "Trending", "Copyright", "var x").forEach { junk ->
            assertFalse(junk, article.text.contains(junk))
        }
    }

    @Test
    fun `a page with too little text is not an article`() {
        assertNull(extractor.extract(page(paragraphs = 0), "https://news.example.org/"))
        assertNull(extractor.extract("<html><body></body></html>", "https://news.example.org/"))
    }

    @Test
    fun `a list item holding paragraphs is read once`() {
        val html = page(paragraphs = 4, extra = "<ul><li><p>Inner point one with words enough.</p></li></ul>")

        val article = requireNotNull(extractor.extract(html, "https://news.example.org/river"))

        assertEquals(1, Regex("Inner point one").findAll(article.text).count())
    }

    private fun page(paragraphs: Int, extra: String = ""): String {
        val body = (0 until paragraphs).joinToString("\n") { i ->
            "<p>Paragraph $i of the story. It has enough words to read like real prose, with commas, and a " +
                "sentence that goes on for a while so the reader view keeps it.</p>"
        }
        return """<html><head><title>The River Story - Example News</title></head><body>
            <nav><ul><li><a href="/">Home</a></li><li><a href="/world">World</a></li></ul></nav>
            <div class="sidebar"><h3>Trending</h3><ul><li><a href="/a">Something else</a></li></ul></div>
            <article><h1>The River Story</h1>$body$extra<script>var x = 1;</script></article>
            <footer>Copyright 2026 Example News. <a href="/privacy">Privacy</a></footer></body></html>"""
    }
}
