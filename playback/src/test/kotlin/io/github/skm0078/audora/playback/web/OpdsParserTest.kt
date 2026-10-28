// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/OpdsParserTest.kt
// Purpose: OPDS catalogue pages give their sections, free downloads in a readable format, "More" and search.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpdsParserTest {
    private val parser = OpdsParser()

    @Test
    fun `a navigation page lists its sections with absolute addresses`() {
        val feed = parser.parse(NAVIGATION, "https://books.example.org/opds/")

        requireNotNull(feed)
        assertEquals("Example Library", feed.title)
        assertEquals(listOf("Popular", "Newest"), feed.entries.map { it.title })
        assertEquals("https://books.example.org/opds/popular", feed.entries[0].section)
        assertEquals("https://books.example.org/new.xml", feed.entries[1].section)
        assertNull(feed.entries[0].download)
        assertEquals("https://books.example.org/opds/?page=2", feed.next)
        assertEquals("https://books.example.org/osd.xml", feed.search?.description)
    }

    @Test
    fun `a book's best readable free download is chosen and paid or unreadable links are left out`() {
        val feed = requireNotNull(parser.parse(BOOKS, "https://books.example.org/opds/popular"))

        val moby = feed.entries.single { it.title == "Moby Dick" }
        assertEquals("Herman Melville", moby.author)
        assertEquals("A whale of a tale.", moby.summary)
        assertEquals(OpdsDownload("https://books.example.org/2701.epub", "application/epub+zip"), moby.download)
        assertEquals("https://books.example.org/2701.opds", moby.section)

        val text = feed.entries.single { it.title == "Plain Text Book" }
        assertEquals("text/plain", text.download?.type)
        assertNull(text.section)

        // Only a Kindle file, and a "buy" link: nothing ListenLock can read for free, and no section either.
        assertEquals(listOf("Moby Dick", "Plain Text Book"), feed.entries.map { it.title })
        assertEquals("https://books.example.org/search?q={searchTerms}", feed.search?.template)
    }

    @Test
    fun `a page that is not an Atom feed is not a catalogue`() {
        assertNull(parser.parse("<html><body><p>Hello</p></body></html>", "https://example.org/"))
        val entry = """<entry xmlns="http://www.w3.org/2005/Atom"><title>x</title></entry>"""
        assertNull(parser.parse(entry, "https://e.org/"))
    }

    @Test
    fun `a huge page is cut to the configured number of entries`() {
        val entries = (1..30).joinToString("") { i ->
            """<entry><title>Book $i</title>""" +
                """<link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/$i.pdf"/></entry>"""
        }
        val feed = OpdsParser(WebBooksConfig(maxEntriesPerPage = 10))
            .parse("""<feed xmlns="http://www.w3.org/2005/Atom"><title>Big</title>$entries</feed>""", "https://e.org/")

        assertEquals(10, feed?.entries?.size)
    }

    @Test
    fun `an OpenSearch description gives its Atom template`() {
        val template = parser.searchTemplate(OPEN_SEARCH, "https://books.example.org/osd.xml")

        assertEquals("https://books.example.org/search.opds/?query={searchTerms}", template)
        assertNull(parser.searchTemplate("<OpenSearchDescription/>", "https://e.org/"))
    }

    @Test
    fun `plain http addresses in a feed are tried as https, which the fetcher accepts`() {
        // Project Gutenberg's OpenSearch description names an http template: search failed with "not an address".
        val described = """<OpenSearchDescription><Url type="application/atom+xml"
            template="http://m.gutenberg.org/ebooks/search.opds/?query={searchTerms}"/></OpenSearchDescription>"""
        assertEquals(
            "https://m.gutenberg.org/ebooks/search.opds/?query={searchTerms}",
            parser.searchTemplate(described, "https://www.gutenberg.org/catalog/osd-books.xml"),
        )

        val feed = requireNotNull(parser.parse(HTTP_LINKS, "https://books.example.org/opds/"))
        val book = feed.entries.single()
        assertEquals("https://books.example.org/1.epub", book.download?.address)
        assertEquals("https://books.example.org/1.opds", book.section)
        assertEquals("https://books.example.org/opds/?page=2", feed.next)
        assertEquals("https://books.example.org/search?q={searchTerms}", feed.search?.template)
        assertEquals("https://books.example.org/osd.xml", feed.search?.description)
    }

    @Test
    fun `entities are not expanded from outside the page`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE feed [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>&x;</title></feed>"""

        val feed = parser.parse(xml, "https://e.org/")

        assertEquals(false, feed?.title.orEmpty().contains("root:"))
    }

    private companion object {
        val NAVIGATION = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Library</title>
              <link rel="self" type="application/atom+xml;profile=opds-catalog;kind=navigation" href="/opds/"/>
              <link rel="next" type="application/atom+xml;profile=opds-catalog" href="?page=2"/>
              <link rel="search" type="application/opensearchdescription+xml" href="/osd.xml"/>
              <entry>
                <title>Popular</title>
                <id>urn:popular</id>
                <link rel="subsection" type="application/atom+xml;profile=opds-catalog;kind=acquisition" href="popular"/>
              </entry>
              <entry>
                <title>Newest</title>
                <link type="application/atom+xml;profile=opds-catalog" href="/new.xml"/>
              </entry>
            </feed>
        """.trimIndent()

        val BOOKS = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Popular</title>
              <link rel="search" type="application/atom+xml" href="/search?q={searchTerms}"/>
              <entry>
                <title>Moby Dick</title>
                <author><name>Herman Melville</name></author>
                <summary type="html">&lt;p&gt;A whale   of a &lt;b&gt;tale&lt;/b&gt;.&lt;/p&gt;</summary>
                <link rel="http://opds-spec.org/acquisition" type="application/x-mobipocket-ebook" href="/2701.mobi"/>
                <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/2701.pdf"/>
                <link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="/2701.epub"/>
                <link rel="alternate" type="application/atom+xml;type=entry;profile=opds-catalog" href="/2701.entry"/>
                <link rel="related" type="application/atom+xml;profile=opds-catalog" href="/2701.opds"/>
                <link rel="http://opds-spec.org/image" type="image/jpeg" href="/2701.jpg"/>
              </entry>
              <entry>
                <title>Plain Text Book</title>
                <link rel="http://opds-spec.org/acquisition" type="text/plain; charset=utf-8" href="/9.txt"/>
              </entry>
              <entry>
                <title>Kindle Only</title>
                <link rel="http://opds-spec.org/acquisition" type="application/x-mobipocket-ebook" href="/7.mobi"/>
                <link rel="http://opds-spec.org/acquisition/buy" type="application/epub+zip" href="/7.epub"/>
              </entry>
            </feed>
        """.trimIndent()

        val HTTP_LINKS = """<feed xmlns="http://www.w3.org/2005/Atom">
              <title>Old Links</title>
              <link rel="next" type="application/atom+xml" href="http://books.example.org/opds/?page=2"/>
              <link rel="search" type="application/atom+xml" href="http://books.example.org/search?q={searchTerms}"/>
              <link rel="search" type="application/opensearchdescription+xml" href="HTTP://books.example.org/osd.xml"/>
              <entry>
                <title>One</title>
                <link rel="http://opds-spec.org/acquisition" type="application/epub+zip"
                  href="http://books.example.org/1.epub"/>
                <link rel="related" type="application/atom+xml;profile=opds-catalog"
                  href="http://books.example.org/1.opds"/>
              </entry>
            </feed>
        """.trimIndent()

        val OPEN_SEARCH = """<?xml version="1.0" encoding="UTF-8"?>
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <ShortName>Example</ShortName>
              <Url type="text/html" template="https://books.example.org/search?q={searchTerms}"/>
              <Url type="application/atom+xml" template="https://books.example.org/search.opds/?query={searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent()
    }
}
