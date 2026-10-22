// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/TextBooks.kt
// Purpose: Opens EPUB and text books from their document URI (plan items 42 and 43): the seam beside BookOpener.

package io.github.skm0078.audora.playback.text

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.DocumentFormats
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Every book source (playback, the reader, the studio and the exports through them) asks [formatOf] first and
 * opens EPUB and text books here, PDFs through PDFium as before. Blocking: call on an IO thread. Errors name the
 * provider's authority only, never the file (its path may be in the URI).
 */
class TextBooks(
    context: Context,
    private val config: TextBookConfig = TextBookConfig(),
    private val library: LibraryConfig = LibraryConfig(),
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

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

    /** Reads and parses an EPUB or text book whole; pages and sentences are then made as they are asked for. */
    fun open(documentUri: String, format: DocumentFormat): Outcome<TextBookText, TextBookError> {
        val uri = Uri.parse(documentUri)
        val document = try {
            when (format) {
                DocumentFormat.EPUB -> readEpub(uri)
                DocumentFormat.TEXT -> readText(uri)
                DocumentFormat.PDF -> Outcome.Failure(TextBookError.Unreadable("a PDF is not a text book"))
            }
        } catch (e: ZipException) {
            Outcome.Failure(TextBookError.NotAnEpub(e.javaClass.simpleName))
        } catch (e: IOException) {
            Outcome.Failure(TextBookError.Unreadable("${e.javaClass.simpleName} authority=${uri.authority}"))
        } catch (e: SecurityException) {
            Outcome.Failure(TextBookError.Unreadable("${e.javaClass.simpleName} authority=${uri.authority}"))
        }
        if (document is Outcome.Failure) AudoraLog.w(TAG, "Text book not opened, format=$format: ${document.error}")
        return document.map { TextBookText(it, config) }
    }

    /** The EPUB's text entries, or TooLarge; a damaged zip throws [ZipException], which [open] reports. */
    private fun readEpub(uri: Uri): Outcome<TextDocument, TextBookError> {
        val entries = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(stream(uri)).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && EpubParser.isTextEntry(it.name) }
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
        return EpubParser.parse(entries)
    }

    private fun readText(uri: Uri): Outcome<TextDocument, TextBookError> {
        val bytes = stream(uri).use { it.readUpTo((config.maxBookBytes + 1).toInt()) }
        val parser = PlainTextParser(config)
        val document = parser.parse(parser.decode(bytes))
        return when {
            bytes.size > config.maxBookBytes -> Outcome.Failure(TextBookError.TooLarge("file over the limit"))
            document.isEmpty -> Outcome.Failure(TextBookError.NoText)
            else -> Outcome.Success(document)
        }
    }

    private fun stream(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("provider returned no stream")

    private companion object {
        const val TAG = "TextBooks"
    }
}
