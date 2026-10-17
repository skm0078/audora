// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryFiles.kt
// Purpose: Where the dictionary lives, whether it is installed, and how its zip is unpacked or removed (item 33).

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Layout under [root] (the no-backup folder: the dictionary is re-downloadable), as the punctuation model's:
 * - `<sha12>/` the dictionary text and its licence, plus an `installed` marker written last, so a kill mid-install
 *   never leaves something that looks complete, and a new pin is a new folder;
 * - `downloads/<id>-<sha12>.partial` a download in progress.
 *
 * Only the two named entries are taken from the zip, already checked against its pin; [maxUnpackedBytes] caps what is
 * written in case a pin is ever wrong.
 */
class DictionaryFiles(
    val root: File,
    private val pin: DictionaryPin,
    private val maxUnpackedBytes: Long,
    private val bufferBytes: Int,
) {
    private val dir = File(root, pin.sha256.take(PIN_PREFIX_CHARS))
    private val marker = File(dir, MARKER)
    val text = File(dir, pin.entryName)

    fun partialDownload(): File =
        File(File(root, DOWNLOADS), "${pin.id}-${pin.sha256.take(PIN_PREFIX_CHARS)}$PARTIAL_SUFFIX")

    /** Deletes partial downloads of other pins (an older build's dictionary), which would never be resumed. */
    fun dropOtherPartials() {
        val current = partialDownload()
        File(root, DOWNLOADS).listFiles().orEmpty()
            .filter { it != current }
            .forEach { if (!it.delete()) AudoraLog.w(TAG, "Stale partial dictionary not deleted") }
    }

    fun isInstalled(): Boolean = marker.isFile && text.isFile && File(dir, pin.licenceName).isFile

    /** Unpacks a verified zip into place and deletes it, whatever the outcome; older pins are removed on success. */
    fun install(zip: File): Outcome<Unit, String> {
        val staging = File(root, STAGING)
        return try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("cannot create ${staging.name}")
            unpack(zip, staging)
            File(staging, MARKER).writeText(pin.sha256, Charsets.UTF_8)
            dir.deleteRecursively()
            if (!staging.renameTo(dir)) throw IOException("cannot move the dictionary into place")
            root.listFiles { file -> file.isDirectory && file.name.length == PIN_PREFIX_CHARS && file != dir }
                ?.forEach { it.deleteRecursively() }
            AudoraLog.i(TAG, "Installed dictionary ${pin.id}")
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Installing dictionary ${pin.id} failed", e)
            Outcome.Failure("could not install the dictionary: ${e.message}")
        } finally {
            staging.deleteRecursively()
            zip.delete()
        }
    }

    fun deleteAll(): Outcome<Unit, String> {
        val clean = !root.exists() || root.deleteRecursively()
        AudoraLog.i(TAG, if (clean) "Deleted the dictionary" else "Could not fully delete the dictionary")
        return if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every dictionary file")
    }

    @Throws(IOException::class)
    private fun unpack(zip: File, into: File) {
        val wanted = setOf(pin.entryName, pin.licenceName)
        try {
            ZipFile(zip).use { unpackWanted(it, wanted, into) }
        } catch (e: ZipException) {
            throw IOException("not a valid zip: ${e.message}", e)
        }
        val missing = wanted.filterNot { File(into, it).isFile }
        if (missing.isNotEmpty()) throw IOException("the zip lacks $missing")
    }

    private fun unpackWanted(archive: ZipFile, wanted: Set<String>, into: File) {
        var written = 0L
        archive.entries().asSequence()
            .filter { !it.isDirectory && it.name in wanted }
            .forEach { entry ->
                written += archive.getInputStream(entry).use { copyCapped(it, File(into, entry.name), written) }
            }
    }

    private fun copyCapped(input: InputStream, target: File, before: Long): Long {
        var total = 0L
        val buffer = ByteArray(bufferBytes)
        target.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (before + total > maxUnpackedBytes) throw IOException("unpacks to more than $maxUnpackedBytes bytes")
                out.write(buffer, 0, read)
            }
        }
        return total
    }

    private companion object {
        const val TAG = "Speech.DictFiles"
        const val DOWNLOADS = "downloads"
        const val STAGING = "staging"
        const val MARKER = "installed"
        const val PARTIAL_SUFFIX = ".partial"
        const val PIN_PREFIX_CHARS = 12
    }
}
