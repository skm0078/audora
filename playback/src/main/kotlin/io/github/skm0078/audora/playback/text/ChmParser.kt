// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/ChmParser.kt
// Purpose: Reads a compiled help file's pages, in its table of contents' order, into chapters
//          (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.nio.charset.Charset

/**
 * `#SYSTEM` names the title and the contents file (.hhc); the contents file lists the pages, each a `text/sitemap`
 * object with a `Name` and a `Local` path, in reading order. Each page listed is a chapter named by its entry, read
 * as an EPUB chapter is ([XhtmlBlocks]); a page listed twice is read once. A help file with no contents file is read
 * page by page in name order. Pictures, scripts and pages the contents leave out are not read.
 */
object ChmParser {
    private const val TAG = "Audora.ChmParser"
    private const val SYSTEM = "/#SYSTEM"
    private const val SYSTEM_CONTENTS = 0
    private const val SYSTEM_TITLE = 3
    private const val SYSTEM_HEADER = 4
    private const val SYSTEM_ENTRY_HEADER = 4
    private val PAGE = Regex("""\.html?$""", RegexOption.IGNORE_CASE)
    private val CHARSET = Regex("""charset\s*=\s*["']?([A-Za-z0-9_.:-]+)""", RegexOption.IGNORE_CASE)
    private const val CHARSET_WINDOW = 2048

    /** What the help file says about itself, for the book's details. */
    data class Info(val title: String?, val contents: String?)

    fun info(chm: ChmFile): Info {
        val system = chm.entry(SYSTEM)?.takeIf { it.section == 0 }?.let(chm::readPlain) ?: return Info(null, null)
        var title: String? = null
        var contents: String? = null
        var at = SYSTEM_HEADER
        while (at + SYSTEM_ENTRY_HEADER <= system.size) {
            val code = system.le16(at)
            val length = system.le16(at + 2)
            val start = at + SYSTEM_ENTRY_HEADER
            if (start + length > system.size) break
            val text = String(system, start, length, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
            when (code) {
                SYSTEM_CONTENTS -> contents = text.ifEmpty { null }
                SYSTEM_TITLE -> title = text.ifEmpty { null }
            }
            at = start + length
        }
        return Info(title, contents)
    }

    fun parse(chm: ChmFile, maxBytes: Long): Outcome<TextDocument, TextBookError> {
        val info = info(chm)
        val contents = (
            info.contents?.let { "/" + it.trimStart('/') } ?: chm.entries.values.firstOrNull {
                it.name.endsWith(".hhc", ignoreCase = true)
            }?.name
            )?.let(chm::entry)
        val listed = contents?.let { entry ->
            chm.read(listOf(entry.name), maxBytes)[entry.name]?.let { topics(decode(it), entry.name) }
        }.orEmpty().filter { chm.entry(it.second) != null }
        val pages = listed.ifEmpty {
            chm.entries.values.filter { it.name.startsWith("/") && PAGE.containsMatchIn(it.name) }
                .sortedBy { it.name.lowercase() }.map { null to it.name }
        }.distinctBy { it.second.lowercase() }
        val bytes = chm.read(pages.map { it.second }, maxBytes)
        val chapters = pages.mapNotNull { (name, path) ->
            val page = chm.entry(path)?.let { bytes[it.name] } ?: return@mapNotNull null
            val blocks = XhtmlBlocks.blocks(MarkupScanner.scan(decode(page)))
            if (blocks.isEmpty()) null else TextChapter(name ?: XhtmlBlocks.firstHeading(blocks), blocks)
        }
        val document = TextDocument(info.title, language = null, chapters = chapters)
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }

    /** The contents file's entries: (name, page path), the path made absolute and its `#anchor` dropped. */
    internal fun topics(hhc: String, hhcPath: String): List<Pair<String?, String>> {
        val base = hhcPath.substringBeforeLast('/', "") + "/"
        val topics = ArrayList<Pair<String?, String>>()
        var params = HashMap<String, String?>()
        for (token in MarkupScanner.scan(hhc)) {
            when {
                token is Markup.Start && token.localName == "object" -> params = HashMap()

                token is Markup.Start && token.localName == "param" -> token.attributes["name"]?.lowercase()?.let {
                    params[it] = token.attributes["value"]?.trim()
                }

                token is Markup.End && token.localName == "object" -> {
                    params["local"]?.let { pagePath(base, it) }?.let { topics += params["name"] to it }
                    params = HashMap()
                }
            }
        }
        return topics
    }

    /** A page inside this file: "sub/page.htm#top" under [base]; null for a link to another file or the web. */
    private fun pagePath(base: String, local: String): String? {
        val path = local.substringBefore('#').replace('\\', '/')
        if (path.isEmpty() || "::" in path || ':' in path.substringBefore('/')) return null
        val absolute = if (path.startsWith("/")) path else base + path
        return "/" + EpubParser.resolve("", absolute.trimStart('/'))
    }

    /** A page's text in the charset its `<meta>` or XML declaration names, else UTF-8 or Windows-1252. */
    internal fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, CHARSET_WINDOW), Charsets.ISO_8859_1)
        val declared = CHARSET.find(head)?.groupValues?.get(1)?.let(::charsetOrNull)
        return when {
            declared != null -> String(bytes, declared)
            else -> EpubParser.decode(bytes).takeIf { '�' !in it } ?: String(bytes, WINDOWS_1252)
        }
    }

    /** A charset Java knows by [name]; null for a misspelt or unsupported one, so the page falls back to UTF-8. */
    private fun charsetOrNull(name: String): Charset? = try {
        Charset.forName(name)
    } catch (e: IllegalArgumentException) {
        AudoraLog.d(TAG, "Unknown page charset: ${e.javaClass.simpleName}")
        null
    }

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    private fun ByteArray.le16(at: Int): Int =
        (this[at].toInt() and BYTE_MASK) or ((this[at + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)
}
