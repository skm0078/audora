// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/SavedPageTest.kt
// Purpose: A saved web page is read for its text: scripts, navigation, forms and fallbacks are left out.

package io.github.skm0078.audora.playback.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPageTest {
    @Test
    fun `reads the article, not the page furniture`() {
        val page = """<!DOCTYPE html><html><head><title>Rain &amp; rivers</title><script>var a = 1;</script></head>
            <body><nav><a href="/">Home</a></nav><noscript>Turn on JavaScript</noscript>
            <h1>Rivers</h1><p>Water&nbsp;moves <b>slowly</b>.</p><form><button>Subscribe</button>
            <select><option>English</option></select></form><ul><li>Ganga</li><li>Padma</li></ul></body></html>"""
        val markup = MarkupScanner.scan(page)
        assertEquals("Rain & rivers", OfficeMeta.firstText(markup, "title"))
        assertEquals(
            listOf(
                BlockKind.HEADING to "Rivers",
                BlockKind.BODY to "Water\u00A0moves slowly.",
                BlockKind.LIST_ITEM to "Ganga",
                BlockKind.LIST_ITEM to "Padma",
            ),
            XhtmlBlocks.blocks(markup).map { it.kind to it.text },
        )
    }
}
