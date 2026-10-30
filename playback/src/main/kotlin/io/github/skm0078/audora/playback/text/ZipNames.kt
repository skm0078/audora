// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/ZipNames.kt
// Purpose: Reads zips whose file names are not UTF-8 (Windows' built-in zip, older tools, many manga CBZs): UTF-8
//          first, as the zip standard and most tools write them, then again with Latin-1.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Java reads a zip's names as UTF-8 and throws at the first one that is not: IllegalArgumentException from
 * [ZipInputStream] and from Android's [ZipFile], ZipException from the desktop JDK's ZipFile. Uncaught, that crashed
 * the reader and the comic maker. The zip is then read again from the start with [FALLBACK], which maps every byte,
 * so ASCII names still match the book's own references and the book opens. Entries flagged as UTF-8 are read as UTF-8
 * either way. A zip that fails the second time too throws as before, for the caller's damaged-file handling.
 */
object ZipNames {
    val FALLBACK: Charset = Charsets.ISO_8859_1

    @PublishedApi
    internal const val TAG = "ZipNames"

    /** [read] over the zip [open] gives; [open] is called again for the second pass, so it must give a new stream. */
    inline fun <T> stream(open: () -> InputStream, read: (ZipInputStream) -> T): T = try {
        ZipInputStream(open()).use(read)
    } catch (e: IllegalArgumentException) {
        AudoraLog.i(TAG, "Zip names are not UTF-8, read as Latin-1: ${e.javaClass.simpleName}")
        ZipInputStream(open(), FALLBACK).use(read)
    }

    /**
     * [read] over [file] as a [ZipFile], with the charset that read it: a name found in one pass is only found again
     * ([ZipFile.getEntry]) with the same charset.
     */
    inline fun <T> file(file: File, read: (ZipFile, Charset) -> T): T {
        val utf8 = try {
            return ZipFile(file).use { read(it, Charsets.UTF_8) }
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: ZipException) {
            e
        }
        AudoraLog.i(TAG, "Zip names are not UTF-8, read as Latin-1: ${utf8.javaClass.simpleName}")
        return try {
            ZipFile(file, FALLBACK).use { read(it, FALLBACK) }
        } catch (e: ZipException) {
            // Damaged after all: thrown as the damage it is, with what the UTF-8 pass found attached.
            e.addSuppressed(utf8)
            throw e
        }
    }
}
