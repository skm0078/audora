// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/details/FileDetailsReader.kt
// Purpose: Reads what a book's file says about itself (title, author, language, cover picture) without reading the
//          book: a PDF's document information, an EPUB's package, Office and OpenDocument properties (ADR-068).

package io.github.skm0078.audora.playback.details

import android.content.Context
import android.net.Uri
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.FileDetails
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.text.FileMeta
import io.github.skm0078.audora.playback.text.TextBooks
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** [details] of a file, and its own cover picture's bytes when it has one (an EPUB), still encoded. */
class FileDetailsRead(val details: FileDetails, val coverImage: ByteArray? = null)

/**
 * Blocking; call on an IO thread. Reads only the small parts that hold the details: an EPUB's container and package
 * (and its cover picture, at most [maxCoverBytes]), a Word file's `docProps/core.xml`, an OpenDocument's `meta.xml`,
 * the head of a web page or RTF file. A file that cannot be read gives what is known (often just the format) and is
 * logged by its provider's authority only, never its name.
 */
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
            when (format) {
                DocumentFormat.PDF -> FileDetailsRead(pdf(uri))
                DocumentFormat.EPUB -> epub(uri)
                DocumentFormat.DOCX -> FileDetailsRead(FileMeta.office(format, zipEntries(uri, setOf(DOCX_CORE))))
                DocumentFormat.ODT -> FileDetailsRead(FileMeta.office(format, zipEntries(uri, setOf(ODT_META))))
                DocumentFormat.HTML -> FileDetailsRead(FileMeta.html(head(uri)))
                DocumentFormat.RTF -> FileDetailsRead(FileMeta.rtf(head(uri)))
                DocumentFormat.TEXT -> FileDetailsRead(FileDetails(format))
            }
        } catch (e: ZipException) {
            unreadable(uri, format, e)
        } catch (e: IOException) {
            unreadable(uri, format, e)
        } catch (e: SecurityException) {
            unreadable(uri, format, e)
        }
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

    private fun zipEntries(uri: Uri, names: Set<String>, limit: Int = headBytes) = zipEntries(uri, limit) {
        it in names
    }

    /** The ZIP's [wanted] entries, each at most [limit] bytes (a larger one is left out). */
    private fun zipEntries(uri: Uri, limit: Int = headBytes, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(stream(uri)).use { zip ->
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

        /** A package, a properties file or a web page's head: far below this. */
        const val DEFAULT_HEAD_BYTES = 256 * 1024

        /** A cover picture larger than this is a poster, not a cover; it is left out. */
        const val DEFAULT_MAX_COVER_BYTES = 8 * 1024 * 1024
    }
}
