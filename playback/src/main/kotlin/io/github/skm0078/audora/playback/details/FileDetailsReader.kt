// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/details/FileDetailsReader.kt
// Purpose: Reads what a book's file says about itself (title, author, language, cover picture) without reading the
//          book: a PDF's document information, an EPUB's package, Office and OpenDocument properties (ADR-068).

package io.github.skm0078.audora.playback.details

import android.content.Context
import android.net.Uri
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.DocumentFormats
import io.github.skm0078.audora.core.library.FileDetails
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.djvu.DjvuException
import io.github.skm0078.audora.playback.djvu.DjvuExpressionException
import io.github.skm0078.audora.playback.djvu.DjvuFile
import io.github.skm0078.audora.playback.djvu.DjvuParser
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.text.ByteArrayChmSource
import io.github.skm0078.audora.playback.text.ChmException
import io.github.skm0078.audora.playback.text.ChmFile
import io.github.skm0078.audora.playback.text.ChmParser
import io.github.skm0078.audora.playback.text.Fb2Parser
import io.github.skm0078.audora.playback.text.FileMeta
import io.github.skm0078.audora.playback.text.MobiFile
import io.github.skm0078.audora.playback.text.TextBooks
import io.github.skm0078.audora.playback.text.ZipNames
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException

/** [details] of a file, and its own cover picture's bytes when it has one (an EPUB), still encoded. */
class FileDetailsRead(val details: FileDetails, val coverImage: ByteArray? = null)

/**
 * Blocking; call on an IO thread. Reads only the small parts that hold the details: an EPUB's container and package
 * (and its cover picture, at most [maxCoverBytes]), a Word file's `docProps/core.xml`, an OpenDocument's `meta.xml`,
 * the head of a web page or RTF file. A file that cannot be read gives what is known (often just the format) and is
 * logged by its provider's authority only, never its name.
 */
@Suppress("TooManyFunctions") // One small reader per file format.
class FileDetailsReader(
    context: Context,
    private val maxCoverBytes: Int = DEFAULT_MAX_COVER_BYTES,
    private val headBytes: Int = DEFAULT_HEAD_BYTES,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val textBooks = TextBooks(appContext)

    fun read(documentUri: String): FileDetailsRead {
        val uri = SharedPdfCopies.readableUri(appContext, documentUri)
        val format = textBooks.formatOf(uri.toString()) ?: return FileDetailsRead(FileDetails(format = null))
        return try {
            read(uri, format)
        } catch (e: ZipException) {
            unreadable(uri, format, e)
        } catch (e: IOException) {
            unreadable(uri, format, e)
        } catch (e: SecurityException) {
            unreadable(uri, format, e)
        } catch (e: IllegalArgumentException) {
            unreadable(uri, format, e)
        }
    }

    private fun read(uri: Uri, format: DocumentFormat): FileDetailsRead = when (format) {
        DocumentFormat.PDF -> FileDetailsRead(pdf(uri))
        DocumentFormat.EPUB -> epub(uri)
        DocumentFormat.DOCX -> FileDetailsRead(FileMeta.office(format, zipEntries(uri, setOf(DOCX_CORE))))
        DocumentFormat.ODT -> FileDetailsRead(FileMeta.office(format, zipEntries(uri, setOf(ODT_META))))
        DocumentFormat.HTML -> FileDetailsRead(FileMeta.html(head(uri)))
        DocumentFormat.RTF -> FileDetailsRead(FileMeta.rtf(head(uri)))
        DocumentFormat.TEXT -> FileDetailsRead(FileDetails(format))
        DocumentFormat.FB2 -> FileDetailsRead(FileMeta.fb2(fb2Head(uri)))
        DocumentFormat.MOBI -> FileDetailsRead(mobi(uri))
        DocumentFormat.CHM -> FileDetailsRead(chm(uri))
        DocumentFormat.DJVU -> FileDetailsRead(djvu(uri))
    }

    private fun pdf(uri: Uri): FileDetails = when (val info = PdfiumEngine.forUri(appContext, uri).documentInfo()) {
        is Outcome.Success -> FileDetails(DocumentFormat.PDF, title = info.value.title, author = info.value.author)

        // A password, or damage: the reader says so when the book is opened; the library keeps the file's name.
        is Outcome.Failure -> FileDetails(DocumentFormat.PDF).also {
            AudoraLog.i(TAG, "No PDF details, authority=${uri.authority}: ${info.error::class.simpleName}")
        }
    }

    /** The package for the details, then the cover picture alone if the package names one. */
    private fun epub(uri: Uri): FileDetailsRead {
        val markup = zipEntries(uri) { it == FileMeta.EPUB_CONTAINER || it.endsWith(OPF_EXTENSION, ignoreCase = true) }
        val pkg = FileMeta.epubPackage(markup) ?: return FileDetailsRead(FileDetails(DocumentFormat.EPUB))
        val cover = pkg.coverPath?.let { path -> zipEntries(uri, setOf(path), maxCoverBytes)[path] }
        return FileDetailsRead(
            FileDetails(DocumentFormat.EPUB, pkg.title, pkg.author, pkg.language, hasCoverImage = cover != null),
            cover,
        )
    }

    /** The start of an FB2, or of the one book in a zipped FB2: the description comes first. */
    private fun fb2Head(uri: Uri): ByteArray {
        val zipped = stream(uri).use { DocumentFormats.isZip(it.readUpTo(ZIP_MAGIC_BYTES)) }
        if (!zipped) return head(uri)
        return ZipNames.stream({ stream(uri) }) { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { Fb2Parser.isWanted(it.name) }
                ?.let { zip.readUpTo(headBytes) } ?: ByteArray(0)
        }
    }

    private fun mobi(uri: Uri): FileDetails =
        when (val read = stream(uri).use { MobiFile.read(it, maxTextBytes = 0, withText = false) }) {
            is Outcome.Success -> FileMeta.mobi(read.value)

            // DRM or damage: the reader says so when the book is opened; the library keeps the file's name.
            is Outcome.Failure -> FileDetails(DocumentFormat.MOBI).also {
                AudoraLog.i(TAG, "No Kindle details, authority=${uri.authority}: ${read.error::class.simpleName}")
            }
        }

    /** A help file's title from its `#SYSTEM` file, which is stored uncompressed near the start. */
    private fun chm(uri: Uri): FileDetails {
        val title = try {
            ChmParser.info(ChmFile.open(ByteArrayChmSource(stream(uri).use { it.readUpTo(MAX_CHM_DETAILS_BYTES) })))
                .title
        } catch (e: ChmException) {
            AudoraLog.i(TAG, "No CHM details, authority=${uri.authority}: ${e.javaClass.simpleName}")
            null
        }
        return FileDetails(DocumentFormat.CHM, title = title)
    }

    /** A DjVu's title and author from its metadata, when its maker wrote any. */
    private fun djvu(uri: Uri): FileDetails = try {
        DjvuFile.open(appContext, uri, TextBookConfig().maxDjvuCopyBytes).use { file ->
            val annotations = file.annotations()
            FileDetails(
                DocumentFormat.DJVU,
                title = DjvuParser.metadata(annotations, "title"),
                author = DjvuParser.metadata(annotations, "author"),
            )
        }
    } catch (e: DjvuException) {
        // Damage: the reader says so when the book is opened; the library keeps the file's name.
        AudoraLog.i(TAG, "No DjVu details, authority=${uri.authority}: ${e.javaClass.simpleName}")
        FileDetails(DocumentFormat.DJVU)
    } catch (e: DjvuExpressionException) {
        AudoraLog.i(TAG, "No DjVu details, authority=${uri.authority}: ${e.javaClass.simpleName}")
        FileDetails(DocumentFormat.DJVU)
    }

    private fun zipEntries(uri: Uri, names: Set<String>, limit: Int = headBytes) = zipEntries(uri, limit) {
        it in names
    }

    /** The ZIP's [wanted] entries, each at most [limit] bytes (a larger one is left out). */
    private fun zipEntries(uri: Uri, limit: Int = headBytes, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        ZipNames.stream({ stream(uri) }) { zip ->
            // A second pass (names not UTF-8) starts afresh.
            entries.clear()
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && wanted(it.name) }
                .forEach { entry ->
                    val bytes = zip.readUpTo(limit + 1)
                    if (bytes.size <= limit) entries[entry.name] = bytes
                }
        }
        return entries
    }

    private fun head(uri: Uri): ByteArray = stream(uri).use { it.readUpTo(headBytes) }

    private fun stream(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("provider returned no stream")

    private fun unreadable(uri: Uri, format: DocumentFormat, e: Exception): FileDetailsRead {
        AudoraLog.w(TAG, "Details unreadable, format=$format authority=${uri.authority}: ${e.javaClass.simpleName}")
        return FileDetailsRead(FileDetails(format))
    }

    private companion object {
        const val TAG = "Library.Details"
        const val DOCX_CORE = "docProps/core.xml"
        const val ODT_META = "meta.xml"
        const val OPF_EXTENSION = ".opf"
        const val ZIP_MAGIC_BYTES = 4

        /** A help file's directory and `#SYSTEM` file sit in its first part; a larger file gives no title here. */
        const val MAX_CHM_DETAILS_BYTES = 4 * 1024 * 1024

        /** A package, a properties file or a web page's head: far below this. */
        const val DEFAULT_HEAD_BYTES = 256 * 1024

        /** A cover picture larger than this is a poster, not a cover; it is left out. */
        const val DEFAULT_MAX_COVER_BYTES = 8 * 1024 * 1024
    }
}
