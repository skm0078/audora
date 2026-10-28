// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/OpdsFeed.kt
// Purpose: Reads one page of an OPDS 1.x catalogue (an Atom feed): its sections, its books and their downloads.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * One catalogue page.
 *
 * @property next the address of the page after this one ("More"), if the catalogue splits its list.
 * @property search how to search this catalogue, if it can be.
 */
data class OpdsFeed(val title: String, val entries: List<OpdsEntry>, val next: String?, val search: OpdsSearch?)

/**
 * A section or a book. [section] is the address of the catalogue page it opens; [download] the file ListenLock can
 * read, when the entry is a book with one. An entry can have both (a book with its own page of formats).
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val section: String?,
    val download: OpdsDownload?,
)

/** A book file: its address and type ([OpdsFormats]). */
data class OpdsDownload(val address: String, val type: String)

/**
 * How a catalogue is searched: [template] holds `{searchTerms}`; or [description] is an OpenSearch description to
 * fetch first, whose Atom template is then used ([OpdsParser.searchTemplate]).
 */
data class OpdsSearch(val template: String?, val description: String?)

/** The book formats ListenLock reads, best first: EPUB keeps chapters and headings, PDF its pages, then plain text. */
object OpdsFormats {
    val READABLE = listOf(
        "application/epub+zip",
        "application/pdf",
        "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.oasis.opendocument.text",
        "application/rtf",
    )

    /** The file name ending for [type], so the saved file opens as what it is. */
    fun extensionOf(type: String): String = EXTENSIONS[baseType(type)] ?: "bin"

    /** "text/plain; charset=utf-8" is "text/plain". */
    fun baseType(type: String): String = type.substringBefore(';').trim().lowercase()

    private val EXTENSIONS = mapOf(
        "application/epub+zip" to "epub",
        "application/pdf" to "pdf",
        "text/plain" to "txt",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.oasis.opendocument.text" to "odt",
        "application/rtf" to "rtf",
    )
}

/**
 * jsoup's XML parser: forgiving of the small mistakes real catalogues make, and it never loads external entities.
 * Addresses in the feed are made absolute against the page's own address, and an http one is tried as https (as
 * [WebAddress] does: the fetcher refuses plain http, and Project Gutenberg's search template is http). Only free
 * downloads count as books (`acquisition` and `acquisition/open-access`); buy, borrow and subscribe links are left
 * out, as is anything in a format ListenLock cannot read. Pure: no network, no Android.
 */
class OpdsParser(private val config: WebBooksConfig = WebBooksConfig()) {
    /** The page in [xml] fetched from [address], or null when it is not an Atom feed. */
    fun parse(xml: String, address: String): OpdsFeed? {
        val document = Jsoup.parse(xml, address, Parser.xmlParser())
        val feed = document.children().firstOrNull { it.normalName() == FEED } ?: return null
        val links = feed.children().filter { it.normalName() == LINK }
        val entries = feed.children()
            .filter { it.normalName() == ENTRY }
            .take(config.maxEntriesPerPage)
            .mapNotNull(::entryOf)
        return OpdsFeed(
            title = childText(feed, TITLE).orEmpty(),
            entries = entries,
            next = links.firstOrNull { rel(it) == NEXT }?.let { address(it, HREF) },
            search = searchOf(links),
        )
    }

    /** The Atom search template in an OpenSearch description, or null when it offers none. */
    fun searchTemplate(xml: String, address: String): String? {
        val document: Document = Jsoup.parse(xml, address, Parser.xmlParser())
        val urls = document.getElementsByTag(URL_TAG).filter { it.hasAttr(TEMPLATE) }
        val atom = urls.firstOrNull { it.attr(TYPE).contains(ATOM, ignoreCase = true) } ?: urls.firstOrNull()
        return atom?.let { address(it, TEMPLATE) }?.takeIf { SEARCH_TERMS in it }
    }

    private fun entryOf(entry: Element): OpdsEntry? = childText(entry, TITLE)?.let { title -> entryOf(entry, title) }

    private fun entryOf(entry: Element, title: String): OpdsEntry? {
        val links = entry.children().filter { it.normalName() == LINK }
        val section = links.firstOrNull { link ->
            val type = link.attr(TYPE)
            type.contains(ATOM, ignoreCase = true) && !type.contains(ENTRY_TYPE, ignoreCase = true) &&
                !isAcquisition(link) && rel(link) !in NOT_SECTIONS
        }?.let { address(it, HREF) }
        val download = links.filter(::isFreeDownload)
            .mapNotNull { link ->
                val type = OpdsFormats.baseType(link.attr(TYPE))
                val rank = OpdsFormats.READABLE.indexOf(type)
                address(link, HREF)?.takeIf { rank >= 0 }?.let { rank to OpdsDownload(it, type) }
            }
            .minByOrNull { it.first }?.second
        if (section == null && download == null) return null
        val author = entry.children().filter { it.normalName() == AUTHOR }
            .mapNotNull { childText(it, NAME) }
            .joinToString(", ")
            .takeIf(String::isNotEmpty)
        val summary = (childText(entry, SUMMARY) ?: childText(entry, CONTENT))?.let(::plainText)
        return OpdsEntry(childText(entry, ID) ?: title, title, author, summary, section, download)
    }

    private fun searchOf(links: List<Element>): OpdsSearch? {
        val search = links.filter { rel(it) == SEARCH }
        val template = search.mapNotNull { address(it, HREF) }.firstOrNull { SEARCH_TERMS in it }
        val description = search.firstOrNull { it.attr(TYPE).contains(OPENSEARCH, ignoreCase = true) }
            ?.let { address(it, HREF) }
        return if (template == null && description == null) null else OpdsSearch(template, description)
    }

    private fun isAcquisition(link: Element) = rel(link).startsWith(ACQUISITION)

    private fun isFreeDownload(link: Element) = rel(link) in FREE_ACQUISITIONS

    private fun rel(link: Element) = link.attr(REL).trim()

    /** The text of [parent]'s first child named [name] (namespace prefix ignored), or null when empty. */
    private fun childText(parent: Element, name: String): String? = parent.children()
        .firstOrNull { it.normalName() == name || it.normalName().substringAfter(':') == name }
        ?.text()?.trim()?.takeIf(String::isNotEmpty)

    /** A summary may be escaped HTML; read as text, with its spaces tidied. */
    private fun plainText(text: String): String = Jsoup.parse(text).text().replace(SPACES, " ").trim()

    private companion object {
        const val FEED = "feed"
        const val ENTRY = "entry"
        const val LINK = "link"
        const val TITLE = "title"
        const val ID = "id"
        const val AUTHOR = "author"
        const val NAME = "name"
        const val SUMMARY = "summary"
        const val CONTENT = "content"
        const val HREF = "href"
        const val REL = "rel"
        const val TYPE = "type"
        const val NEXT = "next"
        const val SEARCH = "search"
        const val ATOM = "atom+xml"

        /** A single entry's own document, not a page of a catalogue. */
        const val ENTRY_TYPE = "type=entry"
        const val OPENSEARCH = "opensearchdescription"
        const val URL_TAG = "Url"
        const val TEMPLATE = "template"
        const val SEARCH_TERMS = "{searchTerms}"
        const val ACQUISITION = "http://opds-spec.org/acquisition"
        val FREE_ACQUISITIONS = setOf(ACQUISITION, "$ACQUISITION/open-access")

        /** Links of an Atom type that are not a section to open: the page itself, its neighbours, its search. */
        val NOT_SECTIONS = setOf("self", "start", "up", NEXT, "previous", "prev", "first", "last", SEARCH, "alternate")
        val SPACES = Regex("""\s+""")
    }
}

/**
 * [element]'s [attribute] made absolute (jsoup keeps a template's `{searchTerms}` braces), with an http address tried
 * as https; null when it is empty or cannot be resolved.
 */
private fun address(element: Element, attribute: String): String? {
    val absolute = element.absUrl(attribute).takeIf(String::isNotEmpty) ?: return null
    return if (absolute.startsWith(HTTP, ignoreCase = true)) HTTPS + absolute.drop(HTTP.length) else absolute
}

private const val HTTP = "http://"
private const val HTTPS = "https://"
