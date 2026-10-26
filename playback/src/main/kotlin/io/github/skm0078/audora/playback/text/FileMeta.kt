// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/FileMeta.kt
// Purpose: Pure readers of a file's own details (ADR-068): EPUB package, Office core properties, OpenDocument meta,
//          a web page's head, an RTF file's info group, an FB2's title-info and a Kindle book's EXTH. Testable on the
//          JVM with byte arrays.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.FileDetails
import io.github.skm0078.audora.core.result.Outcome

object FileMeta {
    const val EPUB_CONTAINER = "META-INF/container.xml"

    /** An EPUB's details from its container and package entries (paths as in the ZIP); null when they are missing. */
    fun epubPackage(entries: Map<String, ByteArray>): EpubDetails? = when (val pkg = EpubParser.packageOf(entries)) {
        is Outcome.Success -> EpubDetails(
            pkg.value.title,
            pkg.value.author,
            pkg.value.language,
            pkg.value.coverPath,
        )

        is Outcome.Failure -> null
    }

    /**
     * A Word file's `docProps/core.xml` or an OpenDocument's `meta.xml`, whichever [entries] holds: `dc:title`,
     * `dc:creator` (else OpenDocument's `meta:initial-creator`) and `dc:language`.
     */
    fun office(format: DocumentFormat, entries: Map<String, ByteArray>): FileDetails {
        val markup = entries.values.firstOrNull()?.let { MarkupScanner.scan(EpubParser.decode(it)) }
            ?: return FileDetails(format)
        return FileDetails(
            format = format,
            title = OfficeMeta.firstText(markup, "dc:title"),
            author = OfficeMeta.firstText(markup, "dc:creator") ?: OfficeMeta.firstText(markup, "meta:initial-creator"),
            language = OfficeMeta.firstText(markup, "dc:language"),
        )
    }

    /** A web page's `<title>`, `<meta name="author">` and `<html lang>`, from the start of the file. */
    fun html(head: ByteArray): FileDetails {
        val markup = MarkupScanner.scan(EpubParser.decode(head))
        val starts = markup.filterIsInstance<Markup.Start>()
        return FileDetails(
            format = DocumentFormat.HTML,
            title = OfficeMeta.firstText(markup, "title"),
            author = starts.firstOrNull { it.localName == "meta" && it.attributes["name"].equals("author", true) }
                ?.attributes?.get("content")?.trim()?.takeIf { it.isNotEmpty() },
            language = starts.firstOrNull { it.localName == "html" }?.let {
                it.attributes["lang"]
                    ?: it.attributes["xml:lang"]
            },
        )
    }

    /** An RTF file's `\info` title and author, from the start of the file (the info group comes first). */
    fun rtf(head: ByteArray): FileDetails {
        val source = String(head, Charsets.ISO_8859_1)
        return FileDetails(
            format = DocumentFormat.RTF,
            title = RtfParser.parse(source).title,
            author = RTF_AUTHOR.find(source)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** An FB2's `title-info`: title, authors and language, from the start of the file (the description comes first). */
    fun fb2(head: ByteArray): FileDetails {
        val info = Fb2Info.of(MarkupScanner.scan(EpubParser.decode(head)))
        return FileDetails(DocumentFormat.FB2, title = info.title, author = info.author, language = info.language)
    }

    /** A Kindle or Mobipocket book's EXTH title, author and language, from its first record ([MobiFile]). */
    fun mobi(content: MobiFile.Content): FileDetails =
        FileDetails(DocumentFormat.MOBI, title = content.title, author = content.author, language = content.language)

    /** `{\author Name}`: plain text only; an author written with escapes is left to the listener. */
    private val RTF_AUTHOR = Regex("""\{\\author\s+([^{}\\]+)\}""")
}

/** What an EPUB's package says; [coverPath] is the cover picture's path in the ZIP. */
data class EpubDetails(val title: String?, val author: String?, val language: String?, val coverPath: String?)
