// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/EpubParser.kt
// Purpose: Reads an EPUB 2 or 3 book's text in reading order, with chapter titles from its contents (plan item 43).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/**
 * Container (`META-INF/container.xml`) → package document (metadata, manifest, spine) → each spine document in order
 * ([XhtmlBlocks]). Chapter titles come from the EPUB 3 navigation document, else the EPUB 2 NCX, else the chapter's
 * first heading. Spine items marked `linear="no"` (pop-up notes, answer keys) are left out. DRM-protected books
 * (an `encryption.xml` over the content) cannot be read and are refused.
 *
 * [entries] maps each ZIP entry's path to its bytes; only text entries need to be there ([isTextEntry]).
 */
object EpubParser {
    private const val TAG = "TextBooks"
    private const val CONTAINER = "META-INF/container.xml"
    private const val ENCRYPTION = "META-INF/encryption.xml"
    private val TEXT_EXTENSIONS = setOf("xml", "opf", "ncx", "xhtml", "html", "htm", "xht")
    private val CONTENT_TYPES = setOf("application/xhtml+xml", "text/html", "application/x-dtbook+xml")
    private val ENCODING = Regex("""encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""")
    private const val DECLARATION_CHARS = 200

    /** Whether a ZIP entry is worth reading into memory: markup only, never images, audio or fonts. */
    fun isTextEntry(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

    fun parse(entries: Map<String, ByteArray>): Outcome<TextDocument, TextBookError> =
        packageOf(entries).flatMap { pkg ->
            val titles = tableOfContents(pkg, entries)
            val chapters = pkg.spine.mapNotNull { item -> chapterOf(item, entries, titles) }
            val document = TextDocument(pkg.title, pkg.language, chapters)
            if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
        }

    internal fun packageOf(entries: Map<String, ByteArray>): Outcome<PackageDocument, TextBookError> {
        val container = entries[CONTAINER]?.let(::decode)
        val opfPath = container?.let { text ->
            MarkupScanner.scan(text).filterIsInstance<Markup.Start>()
                .firstOrNull { it.localName == "rootfile" }?.attributes?.get("full-path")
        }
        val opf = opfPath?.let { entries[it] }?.let(::decode)
        val encrypted = entries[ENCRYPTION]?.let { isContentEncrypted(decode(it)) } == true
        return when {
            container == null -> Outcome.Failure(TextBookError.Damaged("no container"))
            encrypted -> Outcome.Failure(TextBookError.Unreadable("protected by DRM"))
            opf == null || opfPath == null -> Outcome.Failure(TextBookError.Damaged("no package document"))
            else -> Outcome.Success(PackageDocument.parse(MarkupScanner.scan(opf), baseOf(opfPath)))
        }
    }

    private fun chapterOf(
        item: PackageDocument.SpineItem,
        entries: Map<String, ByteArray>,
        titles: Map<String, String>,
    ): TextChapter? {
        val bytes = entries[item.path]?.takeIf { item.mediaType in CONTENT_TYPES } ?: return null
        val blocks = XhtmlBlocks.blocks(MarkupScanner.scan(decode(bytes)))
        return if (blocks.isEmpty()) {
            null
        } else {
            TextChapter(
                titles[item.path] ?: XhtmlBlocks.firstHeading(blocks),
                blocks,
            )
        }
    }

    /** Spine path → chapter title, from the navigation document or the NCX (fragments dropped, first wins). */
    private fun tableOfContents(pkg: PackageDocument, entries: Map<String, ByteArray>): Map<String, String> {
        val nav = pkg.navPath?.let { path ->
            entries[path]?.let { NavTitles.fromNav(MarkupScanner.scan(decode(it)), baseOf(path)) }
        }
        if (!nav.isNullOrEmpty()) return nav
        return pkg.ncxPath?.let { path ->
            entries[path]?.let { NavTitles.fromNcx(MarkupScanner.scan(decode(it)), baseOf(path)) }
        }
            .orEmpty()
    }

    /** Fonts are often obfuscated (listed in encryption.xml) in books without DRM; only encrypted content counts. */
    private fun isContentEncrypted(encryption: String): Boolean =
        MarkupScanner.scan(encryption).filterIsInstance<Markup.Start>()
            .filter { it.localName == "cipherreference" }
            .mapNotNull { it.attributes["uri"] }
            .any { uri -> uri.substringAfterLast('.', "").lowercase() in setOf("xhtml", "html", "htm") }

    /** UTF-8 unless the XML declaration names another encoding (or a UTF-16 byte-order mark says so). */
    internal fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, DECLARATION_CHARS), Charsets.ISO_8859_1)
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16
            else -> ENCODING.find(head)?.groupValues?.get(1)?.let(::charsetOrNull) ?: Charsets.UTF_8
        }
        return String(bytes, charset).removePrefix("")
    }

    private fun charsetOrNull(name: String): Charset? = try {
        Charset.forName(name)
    } catch (e: IllegalCharsetNameException) {
        AudoraLog.d(TAG, "EPUB file names a bad encoding, read as UTF-8: ${e.javaClass.simpleName}")
        null
    } catch (e: UnsupportedCharsetException) {
        AudoraLog.d(TAG, "EPUB file names an unknown encoding, read as UTF-8: ${e.javaClass.simpleName}")
        null
    }

    internal fun baseOf(path: String): String = path.substringBeforeLast('/', "").let {
        if (it.isEmpty()) "" else "$it/"
    }

    /** [href] relative to the folder [base] (which ends in "/" or is empty), percent-decoded, without "./" and "..". */
    internal fun resolve(base: String, href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), Charsets.UTF_8.name())
        val parts = ArrayList<String>()
        for (part in (base + decoded).split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }
}

/** What the package document says: metadata, the manifest's navigation files, and the spine in order. */
@Suppress("LongParameterList") // The package's own fields, each read on its own; grouping them would only rename them.
internal class PackageDocument(
    val title: String?,
    val language: String?,
    val spine: List<SpineItem>,
    val navPath: String?,
    val ncxPath: String?,
    val author: String? = null,
    /** The cover picture's ZIP path: EPUB 3's `cover-image` item, else EPUB 2's `<meta name="cover">`. */
    val coverPath: String? = null,
) {
    class SpineItem(val path: String, val mediaType: String)

    private class ManifestItem(val path: String, val mediaType: String, val properties: Set<String>)

    companion object {
        private const val NCX_TYPE = "application/x-dtbncx+xml"
        private const val IMAGE_TYPE = "image/"

        fun parse(markup: List<Markup>, base: String): PackageDocument {
            val reader = Reader(base)
            markup.forEach(reader::accept)
            return reader.result()
        }
    }

    private class Reader(private val base: String) {
        private val manifest = HashMap<String, ManifestItem>()
        private val spineRefs = ArrayList<String>()
        private var ncxId: String? = null
        private var coverId: String? = null
        private val metadata = HashMap<String, String>()
        private var reading: String? = null

        fun accept(token: Markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.Text -> reading?.let { key -> metadata.putIfAbsentNotBlank(key, token.text.trim()) }
                is Markup.End -> reading = null
            }
        }

        private fun start(token: Markup.Start) {
            val attributes = token.attributes
            when (token.localName) {
                "item" -> item(attributes)
                "spine" -> ncxId = attributes["toc"]
                "itemref" -> attributes["idref"]?.takeIf { attributes["linear"] != "no" }?.let(spineRefs::add)
                "title", "language", "creator" -> reading = token.localName.takeIf { token.name.startsWith("dc:") }
                "meta" -> if (attributes["name"] == "cover") coverId = attributes["content"]
            }
        }

        private fun MutableMap<String, String>.putIfAbsentNotBlank(key: String, value: String) {
            if (key !in this && value.isNotEmpty()) this[key] = value
        }

        private fun item(attributes: Map<String, String>) {
            val id = attributes["id"] ?: return
            val href = attributes["href"] ?: return
            manifest[id] = ManifestItem(
                EpubParser.resolve(base, href),
                attributes["media-type"].orEmpty(),
                attributes["properties"].orEmpty().split(' ').toSet(),
            )
        }

        fun result() = PackageDocument(
            title = metadata["title"],
            language = metadata["language"],
            spine = spineRefs.mapNotNull { manifest[it] }.map { SpineItem(it.path, it.mediaType) },
            navPath = manifest.values.firstOrNull { "nav" in it.properties }?.path,
            ncxPath = ncxId?.let { manifest[it]?.path }
                ?: manifest.values.firstOrNull { it.mediaType == NCX_TYPE }?.path,
            author = metadata["creator"],
            coverPath = (manifest.values.firstOrNull { "cover-image" in it.properties } ?: coverId?.let(manifest::get))
                ?.takeIf { it.mediaType.startsWith(IMAGE_TYPE) }?.path,
        )
    }
}

/** Chapter titles from an EPUB 3 navigation document's `toc` list or an EPUB 2 NCX. */
internal object NavTitles {
    private val WHITESPACE = Regex("\\s+")

    fun fromNav(markup: List<Markup>, base: String): Map<String, String> {
        val reader = NavReader(base)
        markup.forEach(reader::accept)
        return reader.titles
    }

    fun fromNcx(markup: List<Markup>, base: String): Map<String, String> {
        val reader = NcxReader(base)
        markup.forEach(reader::accept)
        return reader.titles
    }

    private fun add(titles: MutableMap<String, String>, path: String, label: CharSequence) {
        val text = label.toString().replace(WHITESPACE, " ").trim()
        if (text.isNotEmpty() && path !in titles) titles[path] = text
    }

    /** Links inside the `nav` whose epub:type is `toc` (nested navs, landmarks and page lists are ignored). */
    private class NavReader(private val base: String) {
        val titles = LinkedHashMap<String, String>()
        private var navDepth = 0
        private var tocDepth = -1
        private var href: String? = null
        private val label = StringBuilder()

        fun accept(token: Markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.Text -> if (href != null) label.append(token.text)
                is Markup.End -> end(token)
            }
        }

        private fun start(token: Markup.Start) {
            if (token.localName == "nav") {
                navDepth++
                if (tocDepth < 0 && "toc" in token.attributes["epub:type"].orEmpty()) tocDepth = navDepth
            } else if (token.localName == "a" && tocDepth > 0) {
                href = token.attributes["href"]
                label.clear()
            }
        }

        private fun end(token: Markup.End) {
            val link = href
            if (token.localName == "a" && link != null) {
                add(titles, EpubParser.resolve(base, link), label)
                href = null
            } else if (token.localName == "nav") {
                if (navDepth == tocDepth) tocDepth = -1
                navDepth--
            }
        }
    }

    /** Each navPoint's label, for the document its content element points at. */
    private class NcxReader(private val base: String) {
        val titles = LinkedHashMap<String, String>()
        private val label = StringBuilder()
        private var inText = false

        fun accept(token: Markup) {
            when (token) {
                is Markup.Start -> when (token.localName) {
                    "navpoint" -> label.clear()
                    "text" -> inText = true
                    "content" -> token.attributes["src"]?.let { add(titles, EpubParser.resolve(base, it), label) }
                }

                is Markup.Text -> if (inText) label.append(token.text)

                is Markup.End -> if (token.localName == "text") inText = false
            }
        }
    }
}
