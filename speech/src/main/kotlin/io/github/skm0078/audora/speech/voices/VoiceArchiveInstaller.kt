// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceArchiveInstaller.kt
// Purpose: Unpacks a verified voice zip into a staging folder, checks its files and swaps it into place.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * The zip's hash was already checked against the catalog, so its content is what the catalog promised; the guards
 * here (paths that climb out of the folder, an unpacked size cap, the voice's files present) are defence in depth
 * against a wrong pin, and the same code path a future source with weaker guarantees would use.
 */
class VoiceArchiveInstaller(private val files: VoiceFiles, private val config: VoiceDownloadConfig) {
    /**
     * Installs [voice] from [zip]. The zip is deleted afterwards unless the phone ran out of space while unpacking:
     * then the verified zip stays, so a retry after freeing space installs it without downloading it again (the
     * downloader verifies a complete file instead of fetching it).
     */
    fun install(zip: File, voice: CatalogVoice): Outcome<InstalledVoice, String> {
        val staging = files.stagingDir(voice.id)
        var keepZip = false
        val result = try {
            staging.deleteRecursively()
            unpack(zip, staging)
            missingFiles(staging, voice.spec)
                .flatMap { files.commit(staging, voice.spec, VoiceOrigin.CATALOG) }
        } catch (e: IOException) {
            keepZip = isOutOfSpace(e)
            AudoraLog.e(TAG, "Unpacking voice=${voice.id} failed, zip kept=$keepZip", e)
            val reason = if (keepZip) "the phone is full; free some space and try again" else e.message
            Outcome.Failure("could not unpack ${voice.id}: $reason")
        } finally {
            staging.deleteRecursively()
            if (!keepZip) zip.delete()
        }
        if (result is Outcome.Success) AudoraLog.i(TAG, "Installed voice=${voice.id} (${result.value.sizeBytes} bytes)")
        return result
    }

    @Throws(IOException::class)
    private fun unpack(zip: File, into: File) {
        if (!into.mkdirs()) throw IOException("cannot create ${into.name}")
        try {
            ZipFile(zip).use { unpackEntries(it, into.canonicalFile) }
        } catch (e: ZipException) {
            throw IOException("not a valid zip: ${e.message}", e)
        }
    }

    private fun unpackEntries(archive: ZipFile, root: File) {
        var unpacked = 0L
        for (entry in archive.entries()) {
            val target = entryTarget(root, entry.name)
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                unpacked += archive.getInputStream(entry).use { copyCapped(it, target, unpacked) }
            }
        }
    }

    /** Where [name] unpacks to; refuses names such as `../x` or absolute paths that would leave [root]. */
    private fun entryTarget(root: File, name: String): File {
        val target = File(root, name).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            throw IOException("entry $name points outside the voice folder")
        }
        return target
    }

    /** Copies one entry and returns its size; fails once everything unpacked so far passes the cap. */
    private fun copyCapped(input: InputStream, target: File, before: Long): Long {
        var total = 0L
        val buffer = ByteArray(config.bufferBytes)
        target.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (before + total > config.maxUnpackedBytes) {
                    throw IOException("unpacks to more than ${config.maxUnpackedBytes} bytes")
                }
                out.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun missingFiles(dir: File, spec: SherpaVoiceSpec): Outcome<Unit, String> {
        val absent = spec.requiredFiles.filterNot { File(dir, it).isFile } +
            listOfNotNull(spec.dataDir).filterNot { File(dir, it).isDirectory }
        return if (absent.isEmpty()) Outcome.Success(Unit) else Outcome.Failure("${spec.id} archive lacks $absent")
    }

    internal companion object {
        private const val TAG = "Speech.VoiceInstall"
        private val OUT_OF_SPACE = listOf("ENOSPC", "No space left")

        /** Android reports a full disk as "ENOSPC (No space left on device)" on the write that failed. */
        internal fun isOutOfSpace(e: IOException): Boolean {
            val message = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return OUT_OF_SPACE.any { it in message }
        }
    }
}
