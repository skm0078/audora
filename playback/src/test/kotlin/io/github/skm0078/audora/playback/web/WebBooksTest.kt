// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/WebBooksTest.kt
// Purpose: A tapped page becomes an article or a book, a catalogue book is saved, and failures leave no files behind.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebBooksTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val source = FakeSource()

    private fun TestScope.books(config: WebBooksConfig = WebBooksConfig()) = WebBooks(
        catalogues = OpdsCatalogues(File(temp.root, CATALOGUES_FILE)),
        newBookFile = { name -> File(temp.newFolder(), name) },
        config = config,
        fetcher = source,
        scope = this,
    )

    @Test
    fun `a web page becomes its article and its temporary file is deleted`() = runTest {
        source.answers["https://news.example.org/river"] = ARTICLE.toByteArray() to "text/html; charset=utf-8"
        val books = books()

        assertTrue(books.readPage("https://news.example.org/river"))
        assertEquals(WebJob(WebJobKind.PAGE, "news.example.org"), books.job.value)
        advanceUntilIdle()

        val result = books.result.value as WebResult.Article
        assertEquals("The River Story", result.article.title)
        assertTrue(result.article.text.contains("Paragraph 3 of the story."))
        assertNull(books.job.value)
        assertTrue("no file left behind", temp.root.walk().none { it.isFile && it.name.endsWith(".html") })
    }

    @Test
    fun `a link straight to a PDF is kept as a book named for its type`() = runTest {
        source.answers["https://example.org/papers/notes"] = "%PDF-1.7 ...".toByteArray() to "application/pdf"
        val books = books()

        books.readPage("https://example.org/papers/notes")
        advanceUntilIdle()

        val book = (books.result.value as WebResult.Book).file
        assertEquals("notes.pdf", book.name)
        assertTrue(book.isFile)
    }

    @Test
    fun `a page with nothing to read says so and keeps nothing`() = runTest {
        source.answers["https://example.org/"] = "<html><body><a href=/>Home</a></body></html>".toByteArray() to
            "text/html"
        val books = books()

        books.readPage("https://example.org/")
        advanceUntilIdle()

        assertEquals(WebResult.Failed(WebJobKind.PAGE, WebError.NoArticle), books.result.value)
        assertTrue(temp.root.walk().none { it.isFile && it.name != "catalogues.txt" })
    }

    @Test
    fun `a catalogue book is saved under its title and a failed download leaves nothing`() = runTest {
        source.answers["https://books.example.org/2701.epub"] = "PK epub".toByteArray() to "application/epub+zip"
        val books = books()

        books.downloadBook("Moby Dick: or, The Whale", OpdsDownload("https://books.example.org/2701.epub", EPUB))
        advanceUntilIdle()
        assertEquals("Moby Dick or, The Whale.epub", (books.result.value as WebResult.Book).file.name)

        books.consume()
        books.downloadBook("Gone", OpdsDownload("https://books.example.org/404.epub", EPUB))
        advanceUntilIdle()
        assertEquals(WebResult.Failed(WebJobKind.BOOK, WebError.Refused(404)), books.result.value)
        assertFalse(temp.root.walk().any { it.name == "Gone.epub" })
    }

    @Test
    fun `one job at a time, and a cancelled one gives no result and keeps no file`() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.answers["https://books.example.org/1.pdf"] = "%PDF".toByteArray() to "application/pdf"
        val books = books()

        assertTrue(books.downloadBook("One", OpdsDownload("https://books.example.org/1.pdf", PDF)))
        advanceUntilIdle()
        assertFalse(books.downloadBook("Two", OpdsDownload("https://books.example.org/1.pdf", PDF)))
        books.cancel()
        advanceUntilIdle()

        assertNull(books.result.value)
        assertNull(books.job.value)
        assertTrue(temp.root.walk().none { it.name == "One.pdf" })
    }

    @Test
    fun `catalogues added before the list was curated stay listed after it until removed`() = runTest {
        File(temp.root, CATALOGUES_FILE).writeText(
            "My Shelf\thttps://books.example.org/opds\nGutenberg again\t${CuratedCatalogue.GUTENBERG.address}\n",
        )
        val catalogues = books().browser
        val curated = CuratedCatalogue.entries.map { it.title }

        assertEquals(curated + "My Shelf", catalogues.all().map { it.title })
        assertFalse(catalogues.all().last().builtIn)

        assertTrue(catalogues.remove("https://books.example.org/opds"))
        assertEquals(curated, catalogues.all().map { it.title })
    }

    @Test
    fun `search fills the template, fetching the OpenSearch description when needed`() = runTest {
        source.answers["https://books.example.org/osd.xml"] = """<OpenSearchDescription>
            <Url type="application/atom+xml" template="/search?q={searchTerms}&amp;page={startPage?}"/>
            </OpenSearchDescription>""".toByteArray() to "application/opensearchdescription+xml"
        val catalogues = books().browser

        assertEquals(
            Outcome.Success("https://books.example.org/search?q=moby+dick&page="),
            catalogues.searchAddress(OpdsSearch(null, "https://books.example.org/osd.xml"), " moby dick "),
        )
        assertEquals(
            Outcome.Success("https://e.org/s?q=%E0%A6%97%E0%A7%80%E0%A6%A4"),
            catalogues.searchAddress(OpdsSearch("https://e.org/s?q={searchTerms}", null), "গীত"),
        )
    }

    @Test
    fun `titles become safe file names`() {
        assertEquals("a b c", WebFiles.safeTitle("a/b\\c"))
        assertEquals("Web page", WebFiles.safeTitle(" ../ "))
        assertEquals("story.html", WebFiles.nameFrom("https://example.org/news/story.php?id=1"))
        assertEquals("example.org.html", WebFiles.nameFrom("https://www.example.org/"))
    }

    /** Answers from [answers] by address (404 for anything else); waits on [gate] first when set. */
    private class FakeSource : WebSource {
        val answers = mutableMapOf<String, Pair<ByteArray, String>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun page(address: String, accept: String): Outcome<WebPage, WebError> {
            val (bytes, type) = answers[address] ?: return Outcome.Failure(WebError.Refused(404))
            return Outcome.Success(WebPage(bytes, type, address))
        }

        override suspend fun download(
            address: String,
            accept: String,
            into: OutputStream,
            onProgress: (Float?) -> Unit,
        ): Outcome<String?, WebError> {
            gate?.await()
            val (bytes, type) = answers[address] ?: return Outcome.Failure(WebError.Refused(404))
            into.write(bytes)
            onProgress(1f)
            return Outcome.Success(type)
        }
    }

    private companion object {
        const val CATALOGUES_FILE = "catalogues.txt"
        const val EPUB = "application/epub+zip"
        const val PDF = "application/pdf"
        val ARTICLE = "<html><head><title>The River Story</title></head><body><nav>Home</nav><article>" +
            (0 until 6).joinToString("") { i ->
                "<p>Paragraph $i of the story. It has enough words to read like real prose, with commas, and a " +
                    "sentence that goes on for a while.</p>"
            } + "</article></body></html>"
    }
}
