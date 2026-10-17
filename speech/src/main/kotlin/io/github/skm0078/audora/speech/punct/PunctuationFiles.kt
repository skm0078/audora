// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/PunctuationFiles.kt
// Purpose: Where the punctuation model lives, whether it is installed, and how its zip is unpacked or removed.

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Layout under [root] (the no-backup folder: the model is re-downloadable):
 * - `<sha12>/` the installed model's two files, plus an `installed` marker written last, so a kill mid-install never
 *   leaves something that looks complete, and a new pin is a new folder;
 * - `downloads/<id>-<sha12>.partial` a download in progress.
 *
 * Only the model's two named files are taken from the zip, wherever they sit in it; the zip was already checked
 * against its pin, and [maxUnpackedBytes] caps what is written in case a pin is ever wrong.
 */
class PunctuationFiles(
    val root: File,
    private val model: PunctuationModel,
    private val maxUnpackedBytes: Long,
    private val bufferBytes: Int,
) {
    val modelDir = File(root, model.sha256.take(PIN_PREFIX_CHARS))
    private val marker = File(modelDir, MARKER)

    fun partialDownload(): File =
        File(File(root, DOWNLOADS), "${model.id}-${model.sha256.take(PIN_PREFIX_CHARS)}$PARTIAL_SUFFIX")

    fun isInstalled(): Boolean = marker.isFile && listOf(model.modelFile, model.vocabFile).all {
        File(modelDir, it).isFile
    }

    /** Unpacks a verified zip into place and deletes it, whatever the outcome; older pins are removed on success. */
    fun install(zip: File): Outcome<Unit, String> {
        val staging = File(root, STAGING)
        return try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("cannot create ${staging.name}")
            unpack(zip, staging)
            File(staging, MARKER).writeText(model.sha256, Charsets.UTF_8)
            modelDir.deleteRecursively()
            if (!staging.renameTo(modelDir)) throw IOException("cannot move the model into place")
            root.listFiles { file -> file.isDirectory && file.name.length == PIN_PREFIX_CHARS && file != modelDir }
                ?.forEach { it.deleteRecursively() }
            AudoraLog.i(TAG, "Installed punctuation model ${model.id}")
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Installing punctuation model ${model.id} failed", e)
            Outcome.Failure("could not install the punctuation model: ${e.message}")
        } finally {
            staging.deleteRecursively()
            zip.delete()
        }
    }

    /** Removes the model and any download. */
    fun deleteAll(): Outcome<Unit, String> {
        val clean = !root.exists() || root.deleteRecursively()
        AudoraLog.i(TAG, if (clean) "Deleted the punctuation model" else "Could not fully delete the punctuation model")
        return if (clean) Outcome.Success(Unit) else Outcome.Failure("could not delete every punctuation model file")
    }

    @Throws(IOException::class)
    private fun unpack(zip: File, into: File) {
        val wanted = setOf(model.modelFile, model.vocabFile)
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
            .filter { !it.isDirectory && it.name.substringAfterLast('/') in wanted }
            .forEach { entry ->
                val target = File(into, entry.name.substringAfterLast('/'))
                written += archive.getInputStream(entry).use { copyCapped(it, target, written) }
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
        const val TAG = "Speech.PunctFiles"
        const val DOWNLOADS = "downloads"
        const val STAGING = "staging"
        const val MARKER = "installed"
        const val PARTIAL_SUFFIX = ".partial"
        const val PIN_PREFIX_CHARS = 12
    }
}
