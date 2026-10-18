// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/download/PinnedZipFiles.kt
// Purpose: Where a pinned zip download (a dictionary, a speech model) lives, whether it is installed, and unpacking.

package io.github.skm0078.audora.speech.download

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.PinnedFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Layout under [root] (a no-backup folder: everything here is re-downloadable):
 * - `<sha12>/` the unpacked [entries], plus an `installed` marker written last, so a kill mid-install never leaves
 *   something that looks complete, and a new pin is a new folder;
 * - `downloads/<id>-<sha12>.partial` a download in progress.
 *
 * Only the named [entries] are taken from the zip, already checked against its [pin]; [maxUnpackedBytes] caps what is
 * written in case a pin is ever wrong. The files are synced before the marker, and an install counts only while the
 * [sizedEntries] together have [sizedBytes], so a power cut or a damaged file reads as not installed (and is
 * downloaded again), never as a truncated file. [install] and [deleteAll] never overlap. [label] names the download
 * in logs and errors ("dictionary", "speech model").
 */
@Suppress("LongParameterList") // A pin and what to take from it; all needed, none derivable.
class PinnedZipFiles(
    val root: File,
    private val pin: PinnedFile,
    private val entries: Set<String>,
    private val sizedEntries: Set<String>,
    private val sizedBytes: Long,
    private val maxUnpackedBytes: Long,
    private val bufferBytes: Int,
    private val label: String,
) {
    /** The folder holding the installed entries. */
    val dir = File(root, pin.sha256.take(PIN_PREFIX_CHARS))
    private val marker = File(dir, MARKER)
    private val lock = Any()

    init {
        require(sizedEntries.all { it in entries }) { "sized entries must be among the entries" }
    }

    /** One installed entry. */
    fun file(entry: String): File = File(dir, entry)

    fun partialDownload(): File =
        File(File(root, DOWNLOADS), "${pin.id}-${pin.sha256.take(PIN_PREFIX_CHARS)}$PARTIAL_SUFFIX")

    /** Deletes partial downloads of other pins (an older build's), which would never be resumed. */
    fun dropOtherPartials() {
        val current = partialDownload()
        File(root, DOWNLOADS).listFiles().orEmpty()
            .filter { it != current }
            .forEach { if (!it.delete()) AudoraLog.w(TAG, "Stale partial $label not deleted") }
    }

    fun isInstalled(): Boolean = marker.isFile && entries.all { File(dir, it).isFile } &&
        sizedEntries.sumOf { File(dir, it).length() } == sizedBytes

    /** Unpacks a verified zip into place and deletes it, whatever the outcome; older pins are removed on success. */
    fun install(zip: File): Outcome<Unit, String> = synchronized(lock) {
        val staging = File(root, STAGING)
        try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("cannot create ${staging.name}")
            unpack(zip, staging)
            val unpacked = sizedEntries.sumOf { File(staging, it).length() }
            if (unpacked != sizedBytes) throw IOException("unpacked $unpacked bytes, pinned $sizedBytes")
            FileOutputStream(File(staging, MARKER)).use { out ->
                out.write(pin.sha256.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            syncFolder(staging)
            dir.deleteRecursively()
            if (!staging.renameTo(dir)) throw IOException("cannot move the $label into place")
            syncFolder(root)
            root.listFiles { file -> file.isDirectory && file.name.length == PIN_PREFIX_CHARS && file != dir }
                ?.forEach { it.deleteRecursively() }
            AudoraLog.i(TAG, "Installed $label ${pin.id}")
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Installing $label ${pin.id} failed: ${e.javaClass.simpleName}")
            Outcome.Failure("could not install the $label: ${e.message}")
        } finally {
            staging.deleteRecursively()
            zip.delete()
        }
    }

    fun deleteAll(): Outcome<Unit, String> = synchronized(lock) {
        val clean = !root.exists() || root.deleteRecursively()
        AudoraLog.i(TAG, if (clean) "Deleted the $label ${pin.id}" else "Could not fully delete the $label ${pin.id}")
        if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every $label file")
    }

    @Throws(IOException::class)
    private fun unpack(zip: File, into: File) {
        val wanted = entries
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
        FileOutputStream(target).use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (before + total > maxUnpackedBytes) throw IOException("unpacks to more than $maxUnpackedBytes bytes")
                out.write(buffer, 0, read)
            }
            out.fd.sync()
        }
        return total
    }

    /** Flushes a folder's renames; best effort, as a file system that refuses still installs correctly. */
    private fun syncFolder(folder: File) {
        try {
            FileChannel.open(folder.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
        } catch (e: UnsupportedOperationException) {
            AudoraLog.w(TAG, "Folder not synced: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "Speech.PinnedZip"
        const val DOWNLOADS = "downloads"
        const val STAGING = "staging"
        const val MARKER = "installed"
        const val PARTIAL_SUFFIX = ".partial"
        const val PIN_PREFIX_CHARS = 12
    }
}
