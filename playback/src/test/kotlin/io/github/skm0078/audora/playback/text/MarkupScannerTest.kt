// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/MarkupScannerTest.kt
// Purpose: The EPUB tokenizer reads tags, attributes and entities, and survives the markup real books contain.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkupScannerTest {
    @Test
    fun `reads tags, attributes and decoded text`() {
        val tokens = MarkupScanner.scan("""<p class="x" epub:type='noteref'>A &amp; B&nbsp;&#2309;&#x0995;</p><br/>""")
        assertEquals(
            listOf(
                Markup.Start("p", mapOf("class" to "x", "epub:type" to "noteref"), selfClosing = false),
                Markup.Text("A & B अক"),
                Markup.End("p"),
                Markup.Start("br", emptyMap(), selfClosing = true),
            ),
            tokens,
        )
    }

    @Test
    fun `drops comments and declarations, keeps CDATA, tolerates junk`() {
        val tokens = MarkupScanner.scan(
            "<?xml version=\"1.0\"?><!DOCTYPE html><!-- note --><p>1 < 2 &unknown; <![CDATA[a<b]]></p>",
        )
        val text = tokens.filterIsInstance<Markup.Text>().joinToString("") { it.text }
        assertEquals("1 < 2 &unknown; a<b", text)
        assertEquals(listOf("p"), tokens.filterIsInstance<Markup.Start>().map { it.name })
    }

    @Test
    fun `a quoted greater-than does not end a tag`() {
        val start = MarkupScanner.scan("""<a title="x > y" href="c.xhtml#n1">""").single() as Markup.Start
        assertEquals("x > y", start.attributes["title"])
        assertEquals("c.xhtml#n1", start.attributes["href"])
    }
}
