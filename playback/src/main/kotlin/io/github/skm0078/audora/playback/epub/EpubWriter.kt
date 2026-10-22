// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/EpubWriter.kt
// Purpose: Packs a copy into an EPUB 3 file: package document, navigation, chapters, and for a talking book its audio.

package io.github.skm0078.audora.playback.epub

import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @property identifier stable for one book (`urn:uuid:…`), so a reading system treats a new copy as the same book.
 * @property summary the accessibility summary, in the app's language.
 */
data class EpubMeta(val identifier: String, val modified: Instant, val summary: String)

/**
 * Blocking. Writes a valid EPUB 3.3 container: the `mimetype` entry first and stored, then the rest. With [audio]
 * (chapter number → its file and timings), chapters get SMIL media overlays and the package declares the auditory
 * access mode and synchronized audio, so accessible-book players (Thorium, Voice Dream, Dolphin EasyReader) read it
 * as a talking book. Audio is stored, not deflated: AAC does not compress further.
 */
class EpubWriter(private val labels: CopyLabels = CopyLabels()) {
    fun write(out: OutputStream, doc: BookDocument, meta: EpubMeta, audio: Map<Int, ChapterAudio> = emptyMap()) {
        val voiced = audio.filterKeys { number -> doc.chapters.any { it.number == number } }
        val xhtml = EpubXhtml(doc, labels)
        ZipOutputStream(out).use { zip ->
            zip.stored(MIMETYPE, EPUB_MIME.toByteArray(Charsets.US_ASCII))
            zip.text("META-INF/container.xml", CONTAINER)
            zip.text(EpubPaths.ROOT + EpubPaths.PACKAGE, EpubPackage(doc, meta, voiced).opf())
            zip.text(EpubPaths.ROOT + EpubPaths.NAV, xhtml.nav())
            zip.text(EpubPaths.ROOT + EpubPaths.STYLE, STYLE)
            doc.chapters.forEach { chapter ->
                if (Thread.interrupted()) throw InterruptedException("EPUB writing cancelled")
                zip.text(EpubPaths.ROOT + EpubPaths.chapter(chapter.number), xhtml.chapter(chapter))
                voiced[chapter.number]?.let { chapterAudio ->
                    zip.text(EpubPaths.ROOT + EpubPaths.overlay(chapter.number), Smil.write(chapter, chapterAudio))
                    zip.storedFile(EpubPaths.ROOT + EpubPaths.audio(chapter.number), chapterAudio.file)
                }
            }
        }
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.stored(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(storedEntry(name, bytes.size.toLong(), crc.value))
        write(bytes)
        closeEntry()
    }

    /** Two passes over [file]: a stored entry needs its CRC before its data. */
    private fun ZipOutputStream.storedFile(name: String, file: File) {
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        putNextEntry(storedEntry(name, file.length(), crc.value))
        file.inputStream().use { it.copyTo(this, BUFFER_BYTES) }
        closeEntry()
    }

    private fun storedEntry(name: String, size: Long, crc: Long) = ZipEntry(name).apply {
        method = ZipEntry.STORED
        this.size = size
        compressedSize = size
        this.crc = crc
    }

    private companion object {
        const val MIMETYPE = "mimetype"
        const val EPUB_MIME = "application/epub+zip"
        const val BUFFER_BYTES = 64 * 1024

        val CONTAINER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="${EpubPaths.ROOT}${EpubPaths.PACKAGE}" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent() + "\n"

        /** Readable defaults only; reading systems apply the reader's own fonts and colours on top. */
        val STYLE = """
            .-epub-media-overlay-active { background-color: #ffe082; color: #000000; }
            table { border-collapse: collapse; margin: 1em 0; }
            th, td { border: 1px solid #888888; padding: 0.2em 0.5em; text-align: start; vertical-align: top; }
            .caption { font-style: italic; }
            .equation { font-family: monospace; }
            .sidebar { border-inline-start: 3px solid #888888; padding-inline-start: 0.8em; }
            .notes { border-top: 1px solid #888888; margin-top: 2em; font-size: 0.9em; }
        """.trimIndent() + "\n"
    }
}

/** The package document (content.opf): metadata with accessibility properties, manifest and spine. */
internal class EpubPackage(
    private val doc: BookDocument,
    private val meta: EpubMeta,
    private val audio: Map<Int, ChapterAudio>,
) {
    fun opf(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\" xml:lang=\"")
        append(doc.lang.bcp47Tag).append("\">\n")
        metadata()
        manifest()
        append("<spine>\n<itemref idref=\"nav\" linear=\"no\"/>\n")
        doc.chapters.forEach { append("<itemref idref=\"c").append(it.number).append("\"/>\n") }
        append("</spine>\n</package>\n")
    }

    private fun StringBuilder.metadata() {
        append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
        append("<dc:identifier id=\"uid\">").append(Xml.escape(meta.identifier)).append("</dc:identifier>\n")
        append("<dc:title>").append(Xml.escape(doc.title)).append("</dc:title>\n")
        (listOf(doc.lang) + (doc.langs - doc.lang)).forEach {
            append("<dc:language>").append(it.bcp47Tag).append("</dc:language>\n")
        }
        property("dcterms:modified", meta.modified.truncatedTo(ChronoUnit.SECONDS).toString())
        accessibility()
        if (audio.isNotEmpty()) overlays()
        append("</metadata>\n")
    }

    private fun StringBuilder.accessibility() {
        val voiced = audio.isNotEmpty()
        property("schema:accessMode", "textual")
        if (voiced) property("schema:accessMode", "auditory")
        property("schema:accessModeSufficient", "textual")
        if (voiced) property("schema:accessModeSufficient", "auditory")
        val features = listOf("structuralNavigation", "tableOfContents", "readingOrder") +
            listOfNotNull(
                "pageNavigation".takeIf { doc.chapters.any { it.pageStarts.isNotEmpty() } },
                "synchronizedAudioText".takeIf { voiced },
            )
        features.forEach { property("schema:accessibilityFeature", it) }
        property("schema:accessibilityHazard", "none")
        property("schema:accessibilitySummary", meta.summary)
    }

    private fun StringBuilder.overlays() {
        audio.values.forEach { chapter ->
            append("<meta property=\"media:duration\" refines=\"#mo").append(chapter.chapter).append("\">")
            append(Smil.clock(chapter.samples, chapter.sampleRate)).append("</meta>\n")
        }
        val totalMs = audio.values.sumOf { it.samples * MS_PER_SECOND / it.sampleRate }
        property("media:duration", Smil.clock(totalMs, MS_PER_SECOND.toInt()))
        property("media:active-class", ACTIVE_CLASS)
    }

    private fun StringBuilder.manifest() {
        append("<manifest>\n")
        item("nav", EpubPaths.NAV, XHTML, properties = "nav")
        item("css", EpubPaths.STYLE, "text/css")
        doc.chapters.forEach { chapter ->
            val number = chapter.number
            val overlay = "mo$number".takeIf { number in audio }
            item("c$number", EpubPaths.chapter(number), XHTML, overlay = overlay)
            if (overlay != null) {
                item(overlay, EpubPaths.overlay(number), "application/smil+xml")
                item("a$number", EpubPaths.audio(number), "audio/mp4")
            }
        }
        append("</manifest>\n")
    }

    private fun StringBuilder.item(
        id: String,
        href: String,
        mediaType: String,
        properties: String? = null,
        overlay: String? = null,
    ) {
        append("<item id=\"").append(id).append("\" href=\"").append(href).append("\" media-type=\"")
        append(mediaType).append('"')
        properties?.let { append(" properties=\"").append(it).append('"') }
        overlay?.let { append(" media-overlay=\"").append(it).append('"') }
        append("/>\n")
    }

    private fun StringBuilder.property(name: String, value: String) {
        append("<meta property=\"").append(name).append("\">").append(Xml.escape(value)).append("</meta>\n")
    }

    private companion object {
        const val XHTML = "application/xhtml+xml"
        const val ACTIVE_CLASS = "-epub-media-overlay-active"
        const val MS_PER_SECOND = 1_000L
    }
}
