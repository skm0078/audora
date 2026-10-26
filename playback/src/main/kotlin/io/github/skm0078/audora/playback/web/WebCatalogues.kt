// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebCatalogues.kt
// Purpose: Browsing OPDS catalogues (CF-15): one page at a time, search, and adding or removing a catalogue.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Each call fetches one page when the listener opens it, in the caller's scope: leaving the page stops the request.
 * Nothing is cached or fetched ahead. Main-safe.
 */
class WebCatalogues(private val catalogues: OpdsCatalogues, private val fetcher: WebSource, config: WebBooksConfig) {
    private val parser = OpdsParser(config)

    /** One catalogue page. */
    suspend fun feed(address: String): Outcome<OpdsFeed, WebError> = withContext(Dispatchers.IO) {
        val read = when (val page = fetcher.page(address, FEED_TYPES)) {
            is Outcome.Failure -> page

            is Outcome.Success -> parser.parse(page.value.text(), page.value.address)
                ?.let { Outcome.Success(it) } ?: Outcome.Failure(WebError.NotACatalogue)
        }
        val host = WebAddress.hostOf(address)
        when (read) {
            is Outcome.Success ->
                AudoraLog.i(TAG, "Catalogue page read, host=$host, entries=${read.value.entries.size}")

            is Outcome.Failure -> AudoraLog.w(TAG, "Catalogue page not read, host=$host: ${read.error}")
        }
        read
    }

    /** The address of [search]'s results for [query]; an OpenSearch description is fetched first when needed. */
    suspend fun searchAddress(search: OpdsSearch, query: String): Outcome<String, WebError> {
        val described = search.template?.let { Outcome.Success(it) } ?: describedTemplate(search.description)
        return when (described) {
            is Outcome.Failure -> described
            is Outcome.Success -> Outcome.Success(fill(described.value, query))
        }
    }

    /**
     * Checks that [typed] is a catalogue, then adds it under its own title. A [WebError] when the address is wrong
     * or not a catalogue; otherwise how the add went.
     */
    suspend fun add(typed: String): Outcome<CatalogueAdd, WebError> {
        val address = WebAddress.normalize(typed) ?: return Outcome.Failure(WebError.BadAddress)
        return when (val feed = feed(address)) {
            is Outcome.Failure -> feed

            is Outcome.Success -> withContext(Dispatchers.IO) {
                val title = feed.value.title.ifBlank { WebAddress.hostOf(address) }
                Outcome.Success(catalogues.add(OpdsCatalogue(title, address)))
            }
        }
    }

    suspend fun remove(address: String): Boolean = withContext(Dispatchers.IO) { catalogues.remove(address) }

    suspend fun all(): List<OpdsCatalogue> = withContext(Dispatchers.IO) { catalogues.all() }

    private suspend fun describedTemplate(description: String?): Outcome<String, WebError> {
        if (description == null) return Outcome.Failure(WebError.NotACatalogue)
        return when (val page = withContext(Dispatchers.IO) { fetcher.page(description, SEARCH_TYPES) }) {
            is Outcome.Failure -> page

            is Outcome.Success -> parser.searchTemplate(page.value.text(), page.value.address)
                ?.let { Outcome.Success(it) } ?: Outcome.Failure(WebError.NotACatalogue)
        }
    }

    companion object {
        private const val TAG = "Audora.Catalogues"
        private const val FEED_TYPES =
            "application/atom+xml;profile=opds-catalog,application/atom+xml;q=0.9,application/xml;q=0.8,*/*;q=0.5"
        private const val SEARCH_TYPES = "application/opensearchdescription+xml,application/xml;q=0.9,*/*;q=0.5"
        private const val SEARCH_TERMS = "{searchTerms}"

        /** By name: the Charset overload needs API 33. */
        private const val UTF_8 = "UTF-8"

        /** OpenSearch's optional parameters ({startPage?}, {language?}, ...) are left empty. */
        private val OPTIONAL_PARAMETER = Regex("""\{[^}]*\?\}""")

        /** [template] with [query] put in; any other optional parameter left empty. */
        fun fill(template: String, query: String): String =
            template.replace(SEARCH_TERMS, URLEncoder.encode(query.trim(), UTF_8))
                .replace(OPTIONAL_PARAMETER, "")
    }
}

/** The charset a `Content-Type` header names, when this phone knows it; null otherwise. */
internal fun charsetOf(contentType: String?): Charset? {
    val name = contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.trim('"')
    return name?.takeIf { it.isNotEmpty() && supported(it) }?.let(Charset::forName)
}

private fun supported(name: String): Boolean = try {
    Charset.isSupported(name)
} catch (ignored: IllegalArgumentException) {
    false
}

/** The page's bytes as text, in the charset the server named (UTF-8 when it named none or one unknown). */
private fun WebPage.text(): String = String(bytes, charsetOf(contentType) ?: Charsets.UTF_8)
