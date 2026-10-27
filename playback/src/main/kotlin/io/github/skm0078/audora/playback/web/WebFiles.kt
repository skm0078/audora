// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebFiles.kt
// Purpose: Writes what a tapped link or Download brings into the app's own book folder, and reads a page's article.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import org.jsoup.Jsoup

/**
 * Everything is written into a new file from [newBookFile]; a failed or cancelled download deletes it (and its
 * folder), so nothing half-written is left behind or added. A link that turns out to be a book file (a PDF, an
 * EPUB, a plain-text book) is kept as that book; a web page is read for its article and then deleted.
 */
internal class WebFiles(
    private val fetcher: WebSource,
    private val newBookFile: (String) -> File,
    private val config: WebBooksConfig,
) {
    private val extractor = WebArticleExtractor(config)

    suspend fun pageOrBook(address: String, onProgress: (Float?) -> Unit): WebResult =
        write(Fetch(WebJobKind.PAGE, nameFrom(address), address, PAGE_TYPES), onProgress) { file, type ->
            val base = type?.let(OpdsFormats::baseType)
            when {
                base != null && base in OpdsFormats.READABLE ->
                    WebResult.Book(withExtension(file, OpdsFormats.extensionOf(base)))

                file.length() > config.maxPageBytes -> WebResult.Failed(WebJobKind.PAGE, WebError.TooBig)

                else -> articleFrom(file, type, address)
            }
        }

    suspend fun book(download: OpdsDownload, title: String, onProgress: (Float?) -> Unit): WebResult {
        val name = "$title.${OpdsFormats.extensionOf(download.type)}"
        val fetch = Fetch(WebJobKind.BOOK, name, download.address, BOOK_TYPES)
        return write(fetch, onProgress) { file, _ -> WebResult.Book(file) }
    }

    /** One download: what it is for, the file name to save it under, where from, and the types asked for. */
    private class Fetch(val kind: WebJobKind, val name: String, val address: String, val accept: String)

    /**
     * Downloads [fetch] into a new file and hands it with its content type to [finish]; the file is kept only when
     * [finish] returns it as a [WebResult.Book].
     */
    private suspend fun write(
        fetch: Fetch,
        onProgress: (Float?) -> Unit,
        finish: (File, String?) -> WebResult,
    ): WebResult {
        val file = newBookFile(fetch.name)
        var result: WebResult? = null
        try {
            val written = file.outputStream().use { out ->
                fetcher.download(fetch.address, fetch.accept, out, onProgress)
            }
            result = when (written) {
                is Outcome.Failure -> WebResult.Failed(fetch.kind, written.error)
                is Outcome.Success -> finish(file, written.value)
            }
        } catch (e: IOException) {
            result = WebResult.Failed(fetch.kind, WebError.NotSaved(e.javaClass.simpleName))
        } finally {
            if (result !is WebResult.Book) file.parentFile?.deleteRecursively()
        }
        return result
    }

    private fun articleFrom(file: File, contentType: String?, address: String): WebResult {
        // The header's charset when the phone knows it; else jsoup reads the page's own <meta charset>.
        val document = Jsoup.parse(file, charsetOf(contentType)?.name(), address)
        val article = extractor.extract(document, address)
        return article?.let { WebResult.Article(it) } ?: WebResult.Failed(WebJobKind.PAGE, WebError.NoArticle)
    }

    /** A page saved under the name of the book type it turned out to be, so the library reads it as that. */
    private fun withExtension(file: File, extension: String): File {
        val renamed = File(file.parentFile, file.nameWithoutExtension + "." + extension)
        return if (renamed == file || file.renameTo(renamed)) renamed else file
    }

    companion object {
        private const val PAGE_TYPES =
            "text/html,application/xhtml+xml;q=0.9,application/pdf;q=0.8,application/epub+zip;q=0.8,*/*;q=0.5"
        private const val BOOK_TYPES = "application/epub+zip,application/pdf,text/plain;q=0.9,*/*;q=0.5"
        private const val FALLBACK_NAME = "Web page"
        private const val MAX_TITLE_CHARS = 100
        private const val PAGE_EXTENSION = ".html"
        private val UNSAFE = Regex("""[/\\:*?"<>|\u0000-\u001f]""")
        private val SPACES = Regex("""\s+""")

        /** A file name from a book's title: no path or reserved characters, not too long, never hidden. */
        fun safeTitle(title: String): String = title.replace(UNSAFE, " ").replace(SPACES, " ").trim()
            .take(MAX_TITLE_CHARS).trimStart('.').trim().ifBlank { FALLBACK_NAME }

        /** The last part of the address's path, as a file name for whatever it turns out to be. */
        fun nameFrom(address: String): String {
            val last = try {
                URI(address).path.orEmpty().trimEnd('/').substringAfterLast('/')
            } catch (ignored: URISyntaxException) {
                ""
            }
            return safeTitle(last.substringBeforeLast('.').ifBlank { WebAddress.hostOf(address) }) + PAGE_EXTENSION
        }
    }
}
