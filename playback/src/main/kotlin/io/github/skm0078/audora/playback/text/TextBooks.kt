// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/TextBooks.kt
// Purpose: Opens text books (EPUB, Word, OpenDocument, RTF, HTML, Markdown, FB2, Kindle, CHM, DjVu, text) by URI:
//          the seam beside BookOpener.

package io.github.skm0078.audora.playback.text

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.DocumentFormats
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.djvu.DjvuException
import io.github.skm0078.audora.playback.djvu.DjvuExpressionException
import io.github.skm0078.audora.playback.djvu.DjvuFile
import io.github.skm0078.audora.playback.djvu.DjvuParser
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Every book source (playback, the reader, the studio and the exports through them) asks [formatOf] first and
 * opens text books here (ADR-042, ADR-066, ADR 2026-10-26-more-ebook-formats), PDFs through PDFium as before.
 * Blocking: call on an IO thread. Errors name the provider's authority only, never the file (its path may be in the
 * URI).
 */
@Suppress("TooManyFunctions") // One small reader per file format.
class TextBooks(
    context: Context,
    private val config: TextBookConfig = TextBookConfig(),
    private val library: LibraryConfig = LibraryConfig(),
) {
    private val appContext: Context = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    /** The book's format by its first bytes; null when it cannot be read now (the PDF path then reports why). */
    fun formatOf(documentUri: String): DocumentFormat? {
        val uri = Uri.parse(documentUri)
        return try {
            stream(uri).use {
                DocumentFormats.sniff(it.readUpTo(library.pdfHeaderWindowBytes), library.pdfHeaderWindowBytes)
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Format unknown, authority=${uri.authority}: ${e.javaClass.simpleName}")
            null
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Format unknown, authority=${uri.authority}: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Reads and parses a text book whole; pages and sentences are then made as they are asked for. */
    fun open(documentUri: String, format: DocumentFormat): Outcome<TextBookText, TextBookError> {
        val uri = Uri.parse(documentUri)
        val document = try {
            read(uri, format)
        } catch (e: ZipException) {
            Outcome.Failure(TextBookError.Damaged(e.javaClass.simpleName))
        } catch (e: IOException) {
            Outcome.Failure(TextBookError.Unreadable("${e.javaClass.simpleName} authority=${uri.authority}"))
        } catch (e: SecurityException) {
            Outcome.Failure(TextBookError.Unreadable("${e.javaClass.simpleName} authority=${uri.authority}"))
        }
        when (document) {
            is Outcome.Success ->
                AudoraLog.i(TAG, "Text book opened, format=$format chapters=${document.value.chapters.size}")

            is Outcome.Failure -> AudoraLog.w(TAG, "Text book not opened, format=$format: ${document.error}")
        }
        return document.map { TextBookText(it, config) }
    }

    private fun read(uri: Uri, format: DocumentFormat): Outcome<TextDocument, TextBookError> = when (format) {
        DocumentFormat.EPUB -> readZip(uri, EpubParser::isTextEntry).flatMap(EpubParser::parse)
        DocumentFormat.DOCX -> readZip(uri, DocxParser::isWanted).flatMap(DocxParser::parse)
        DocumentFormat.ODT -> readZip(uri, OdtParser::isWanted).flatMap(OdtParser::parse)
        DocumentFormat.TEXT -> readText(uri)
        DocumentFormat.RTF -> readRtf(uri)
        DocumentFormat.HTML -> readHtml(uri)
        DocumentFormat.FB2 -> readFb2(uri)
        DocumentFormat.MOBI -> readMobi(uri)
        DocumentFormat.CHM -> readChm(uri)
        DocumentFormat.DJVU -> readDjvu(uri)
        DocumentFormat.PDF -> Outcome.Failure(TextBookError.Unreadable("a PDF is not a text book"))
    }

    /**
     * The ZIP's [wanted] entries (an EPUB's markup, a Word file's text parts), or TooLarge; images, fonts and other
     * media are never read. A damaged zip throws [ZipException], which [open] reports.
     */
    private fun readZip(uri: Uri, wanted: (String) -> Boolean): Outcome<Map<String, ByteArray>, TextBookError> {
        val entries = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(stream(uri)).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && wanted(it.name) }
                .forEach { entry ->
                    val bytes = zip.readUpTo((config.maxBookBytes - total + 1).toInt().coerceAtLeast(1))
                    total += bytes.size
                    if (total >
                        config.maxBookBytes
                    ) {
                        return Outcome.Failure(TextBookError.TooLarge("text over the limit"))
                    }
                    entries[entry.name] = bytes
                }
        }
        return Outcome.Success(entries)
    }

    /** A text file, or Markdown when its content shows it ([MarkdownParser.looksLikeMarkdown]). */
    private fun readText(uri: Uri): Outcome<TextDocument, TextBookError> = readWhole(uri) { text ->
        if (MarkdownParser.looksLikeMarkdown(text)) MarkdownParser.parse(text) else PlainTextParser(config).parse(text)
    }

    /** RTF's text read like a text file's, its footnotes as paragraphs at the end. */
    private fun readRtf(uri: Uri): Outcome<TextDocument, TextBookError> = readWhole(uri) { source ->
        val rtf = RtfParser.parse(source)
        val text = (listOf(rtf.text) + rtf.notes).joinToString("\n\n")
        PlainTextParser(config).parse(text).copy(title = rtf.title)
    }

    /** A saved page or HTML file: its body as [XhtmlBlocks] reads an EPUB chapter, one chapter titled by `<title>`. */
    private fun readHtml(uri: Uri): Outcome<TextDocument, TextBookError> = readWhole(uri) { source ->
        val markup = MarkupScanner.scan(source)
        val blocks = XhtmlBlocks.blocks(markup)
        val title = OfficeMeta.firstText(markup, "title")
        val chapter = TextChapter(title ?: XhtmlBlocks.firstHeading(blocks), blocks)
        TextDocument(title, language = null, chapters = if (blocks.isEmpty()) emptyList() else listOf(chapter))
    }

    /** An FB2, or an FB2 zipped alone (.fb2.zip), which its first bytes tell apart. */
    private fun readFb2(uri: Uri): Outcome<TextDocument, TextBookError> {
        val zipped = stream(uri).use { DocumentFormats.isZip(it.readUpTo(ZIP_MAGIC_BYTES)) }
        return if (zipped) {
            readZip(uri, Fb2Parser::isWanted).flatMap(Fb2Parser::parseZip)
        } else {
            readBytes(uri).flatMap(Fb2Parser::parse)
        }
    }

    /** A Kindle or Mobipocket book without DRM, or a PalmDOC, read once from the start ([MobiFile]). */
    private fun readMobi(uri: Uri): Outcome<TextDocument, TextBookError> =
        stream(uri).use { MobiFile.read(it, config.maxBookBytes) }.flatMap { content ->
            val document = MobiParser.parse(content) { PlainTextParser(config).parse(it) }
            if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
        }

    /** A help file, read at random places: through its file descriptor when it has one, else whole in memory. */
    private fun readChm(uri: Uri): Outcome<TextDocument, TextBookError> = try {
        readChmOrThrow(uri)
    } catch (e: ChmException) {
        Outcome.Failure(TextBookError.Damaged("CHM: ${e.message}"))
    } catch (e: ChmTooLargeException) {
        Outcome.Failure(TextBookError.TooLarge(e.javaClass.simpleName))
    }

    private fun readChmOrThrow(uri: Uri): Outcome<TextDocument, TextBookError> {
        val descriptor = resolver.openFileDescriptor(uri, READ_MODE)
            ?: return ChmParser.parse(ChmFile.open(wholeChm(uri)), config.maxBookBytes)
        // The stream owns the descriptor: closing it closes both, once.
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val channel = input.channel
            // A pipe has no size and cannot be read at a place; such a provider's file is read whole.
            val size = try {
                channel.size()
            } catch (e: IOException) {
                AudoraLog.d(TAG, "CHM descriptor has no size, read whole: ${e.javaClass.simpleName}")
                0L
            }
            val source = if (size > 0) FileChannelChmSource(channel) else wholeChm(uri)
            ChmParser.parse(ChmFile.open(source), config.maxBookBytes)
        }
    }

    /**
     * A DjVu's hidden text layer, read in place by DjVuLibre (ADR 2026-10-27-djvu-books). A scan without one became a
     * photo book when it was added, so here it is NoText.
     */
    private fun readDjvu(uri: Uri): Outcome<TextDocument, TextBookError> = try {
        DjvuFile.open(appContext, uri, config.maxDjvuCopyBytes).use { DjvuParser.parse(it, config.maxBookBytes) }
    } catch (e: DjvuException) {
        Outcome.Failure(TextBookError.Damaged("DjVu: ${e.message}"))
    } catch (e: DjvuExpressionException) {
        Outcome.Failure(TextBookError.Damaged("DjVu text layer: ${e.message}"))
    }

    /** A provider whose descriptor cannot seek (a pipe): the help file is read whole, up to the size limit. */
    private fun wholeChm(uri: Uri): ChmSource {
        val bytes = stream(uri).use { it.readUpTo((config.maxBookBytes + 1).toInt()) }
        if (bytes.size > config.maxBookBytes) throw ChmTooLargeException()
        return ByteArrayChmSource(bytes)
    }

    private fun readBytes(uri: Uri): Outcome<ByteArray, TextBookError> {
        val bytes = stream(uri).use { it.readUpTo((config.maxBookBytes + 1).toInt()) }
        return if (bytes.size > config.maxBookBytes) {
            Outcome.Failure(TextBookError.TooLarge("file over the limit"))
        } else {
            Outcome.Success(bytes)
        }
    }

    /** The whole file decoded as text ([PlainTextParser.decode]) and parsed, or TooLarge or NoText. */
    private fun readWhole(uri: Uri, parse: (String) -> TextDocument): Outcome<TextDocument, TextBookError> {
        val bytes = stream(uri).use { it.readUpTo((config.maxBookBytes + 1).toInt()) }
        if (bytes.size > config.maxBookBytes) return Outcome.Failure(TextBookError.TooLarge("file over the limit"))
        val document = parse(PlainTextParser(config).decode(bytes))
        return if (document.isEmpty) Outcome.Failure(TextBookError.NoText) else Outcome.Success(document)
    }

    private fun stream(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("provider returned no stream")

    private companion object {
        const val TAG = "TextBooks"
        const val READ_MODE = "r"
        const val ZIP_MAGIC_BYTES = 4
    }
}
