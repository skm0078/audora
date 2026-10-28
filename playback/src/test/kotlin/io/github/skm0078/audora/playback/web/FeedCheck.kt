// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/web/FeedCheck.kt
// Purpose: Checks one live catalogue the way a listener uses it: first page, a readable book a few sections down,
//          that book's download answering, and search. Used only by the weekly live check.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** What the check found; [problem] is null when the catalogue works. */
internal data class FeedReport(
    val name: String,
    val address: String,
    val entries: Int = 0,
    val book: String? = null,
    val download: String? = null,
    val search: String? = null,
    val problem: String? = null,
)

/**
 * The app's own [WebCatalogues] (same fetcher, Accept header and parser) reads the pages, so a pass here means the
 * phone can read them too. Requests are bounded: at most [maxPages] catalogue pages, [maxDepth] sections deep, and
 * the download is only started (its first bytes asked for), never fetched whole.
 */
internal class FeedCheck(
    catalogueFile: File,
    private val config: WebBooksConfig = WebBooksConfig(),
    private val maxPages: Int = 12,
    private val maxDepth: Int = 3,
) {
    private val fetcher = WebFetcher(config)
    private val browser = WebCatalogues(OpdsCatalogues(catalogueFile), fetcher, config)

    suspend fun check(name: String, address: String): FeedReport {
        val start = FeedReport(name, address)
        return when (val first = browser.feed(address)) {
            is Outcome.Failure -> start.copy(problem = "first page: ${first.error}${seen(address, first.error)}")
            is Outcome.Success -> checkFrom(start.copy(entries = first.value.entries.size), first.value)
        }
    }

    /** For a page that is not a catalogue: what came instead (its type and first characters), to see why. */
    private suspend fun seen(address: String, error: WebError): String {
        val fetched = if (error == WebError.NotACatalogue) fetcher.page(address, ANY) else null
        return (fetched as? Outcome.Success)?.value?.let {
            val start = String(it.bytes.take(SNIPPET_BYTES).toByteArray()).replace(SPACES, " ")
            " (got ${it.contentType} from ${it.address}: $start)"
        }.orEmpty()
    }

    private suspend fun checkFrom(start: FeedReport, first: OpdsFeed): FeedReport {
        val book = firstBook(first)
        val download = book?.download?.let(::answer)
        val search = first.search?.let { search(it) }
        val problem = when {
            first.entries.isEmpty() -> "first page lists nothing"
            book == null -> "no readable book within $maxDepth sections"
            download?.startsWith(OK) != true -> "download: $download"
            search != null && !search.startsWith(OK) -> "search: $search"
            else -> null
        }
        return start.copy(book = book?.title, download = download, search = search, problem = problem)
    }

    /** Breadth first: the first entry with a download, opening at most a few sections of each page. */
    private suspend fun firstBook(first: OpdsFeed): OpdsEntry? {
        var level = listOf(first)
        var pages = 1
        repeat(maxDepth + 1) {
            level.firstNotNullOfOrNull { feed -> feed.entries.firstOrNull { it.download != null } }?.let { return it }
            val sections = level.flatMap { feed -> feed.entries.mapNotNull { it.section }.take(SECTIONS_PER_PAGE) }
            val next = mutableListOf<OpdsFeed>()
            for (section in sections) {
                if (pages >= maxPages) break
                pages++
                (browser.feed(section) as? Outcome.Success)?.let { next += it.value }
            }
            level = next
        }
        return null
    }

    private suspend fun search(search: OpdsSearch): String = when (val found = browser.searchAddress(search, QUERY)) {
        is Outcome.Failure -> "no search address: ${found.error}"

        is Outcome.Success -> when (val page = browser.feed(found.value)) {
            is Outcome.Failure -> "results page ${found.value}: ${page.error}"
            is Outcome.Success -> "$OK ${page.value.entries.size} results for \"$QUERY\""
        }
    }

    /**
     * Starts the download as [WebFetcher] would (https only, https redirects followed) and reads only the answer:
     * "ok <code> <type>", or why not.
     */
    private fun answer(download: OpdsDownload): String {
        val uri = URI(download.address)
        if (!uri.scheme.equals(HTTPS, ignoreCase = true)) return "not https (${uri.scheme}), the app refuses it"
        val connection = uri.toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = config.connectTimeoutMs
            connection.readTimeout = config.readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", WebFetcher.USER_AGENT)
            connection.setRequestProperty("Range", "bytes=0-1023")
            val code = connection.responseCode
            if (code in SUCCESS) "$OK $code ${connection.contentType}" else "HTTP $code"
        } catch (e: IOException) {
            "unreachable: ${e.javaClass.simpleName}"
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val OK = "ok"
        const val HTTPS = "https"
        const val QUERY = "love"
        const val SECTIONS_PER_PAGE = 3
        const val ANY = "*/*"
        const val SNIPPET_BYTES = 160
        val SPACES = Regex("""\s+""")
        val SUCCESS = 200..299
    }
}
