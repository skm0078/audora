// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/BackupArchive.kt
// Purpose: Writes a backup zip and unpacks a picked one into a staging folder, refusing anything unsafe (item 28).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.config.BackupConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The first entry of every backup: which format and database version wrote it. */
data class BackupManifest(val format: Int, val databaseVersion: Int, val createdAtMs: Long) {
    fun encode(): ByteArray = Properties().apply {
        setProperty(FORMAT, format.toString())
        setProperty(DATABASE_VERSION, databaseVersion.toString())
        setProperty(CREATED_AT, createdAtMs.toString())
    }.let { properties ->
        ByteArrayOutputStream().also { properties.store(it, null) }.toByteArray()
    }

    companion object {
        /** Bump when an older app could no longer read a backup this app writes. */
        const val CURRENT_FORMAT = 1
        const val ENTRY = "manifest.properties"
        private const val FORMAT = "format"
        private const val DATABASE_VERSION = "databaseVersion"
        private const val CREATED_AT = "createdAtMs"

        /** Null unless all three fields are there and numeric. */
        fun decode(bytes: ByteArray): BackupManifest? {
            val properties = Properties().apply { load(bytes.inputStream()) }
            val format = properties.getProperty(FORMAT)?.toIntOrNull()
            val version = properties.getProperty(DATABASE_VERSION)?.toIntOrNull()
            val created = properties.getProperty(CREATED_AT)?.toLongOrNull()
            return if (format != null && version != null && created != null) {
                BackupManifest(format, version, created)
            } else {
                null
            }
        }
    }
}

/** Why a backup could not be written or restored; the UI says each in plain words. */
enum class BackupError {
    /** Not a backup Audora wrote: no manifest first, an unreadable one, or no database. */
    NOT_A_BACKUP,

    /** Written by a newer Audora (a newer format or database); update the app first. */
    FROM_NEWER_APP,

    /** Unpacks to more than the limits allow. */
    TOO_LARGE,

    /** Reading or writing failed (the file went away, storage full, a damaged zip). */
    IO_FAILED,
}

/** What a backup holds: [files] entries besides the manifest, [bytes] before compression. */
data class BackupSummary(val files: Int, val bytes: Long)

class BackupArchive(
    private val contents: BackupContents = BackupContents.DEFAULT,
    private val config: BackupConfig = BackupConfig(),
) {
    /**
     * Writes the manifest, the [database] snapshot as [BackupContents.DATABASE] and every allowed file under
     * [root] to [out], which the caller closes. Throws IOException; the caller deletes the half-written file.
     */
    fun write(out: OutputStream, manifest: BackupManifest, database: File, root: File): BackupSummary {
        val entries = listOf(BackupContents.DATABASE to database) + contents.existing(root)
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
        zip.write(manifest.encode())
        zip.closeEntry()
        var bytes = 0L
        entries.forEach { (name, file) ->
            zip.putNextEntry(ZipEntry(name))
            bytes += file.inputStream().use { it.copyTo(zip, config.bufferBytes) }
            zip.closeEntry()
        }
        zip.finish()
        return BackupSummary(entries.size, bytes)
    }

    /**
     * Unpacks [input] into [staging] (emptied first) and returns the manifest. The manifest must come first, so a
     * newer or foreign file is refused before anything is unpacked. Entries outside the allow-list are skipped. On
     * any failure [staging] is deleted.
     */
    fun stage(input: InputStream, staging: File, databaseVersion: Int): Outcome<BackupManifest, BackupError> {
        staging.deleteRecursively()
        val result = try {
            ZipInputStream(input).use { zip -> Unpack(zip, staging, databaseVersion).run() }
        } catch (e: ZipException) {
            AudoraLog.w(TAG, "Backup unreadable: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.NOT_A_BACKUP)
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Backup not unpacked: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.IO_FAILED)
        }
        if (result is Outcome.Failure) staging.deleteRecursively()
        return result
    }

    /** One unpacking pass; throws IOException for a read or write failure. */
    private inner class Unpack(
        private val zip: ZipInputStream,
        private val staging: File,
        private val databaseVersion: Int,
    ) {
        private val buffer = ByteArray(config.bufferBytes)
        private var total = 0L
        private var count = 0
        private var skipped = 0

        fun run(): Outcome<BackupManifest, BackupError> {
            val manifest = readManifest()
            val refusal = when {
                manifest == null -> BackupError.NOT_A_BACKUP
                manifest.format > BackupManifest.CURRENT_FORMAT -> BackupError.FROM_NEWER_APP
                manifest.databaseVersion > databaseVersion -> BackupError.FROM_NEWER_APP
                else -> unpackAll()
            }
            if (refusal != null) {
                AudoraLog.w(
                    TAG,
                    "Backup refused: $refusal, format ${manifest?.format}, db ${manifest?.databaseVersion}",
                )
                return Outcome.Failure(refusal)
            }
            AudoraLog.i(TAG, "Backup staged: $count files, $total bytes, $skipped entries skipped")
            return Outcome.Success(requireNotNull(manifest))
        }

        private fun readManifest(): BackupManifest? {
            val first = zip.nextEntry
            if (first?.name != BackupManifest.ENTRY) return null
            val bytes = readBounded(ByteArrayOutputStream(), MANIFEST_MAX_BYTES)?.toByteArray()
            return bytes?.let(BackupManifest::decode)
        }

        /** Null when everything fit; otherwise why the backup is refused. */
        private fun unpackAll(): BackupError? {
            var error: BackupError? = null
            var entry = zip.nextEntry
            while (entry != null && error == null) {
                error = unpackEntry(entry)
                entry = zip.nextEntry
            }
            return error ?: BackupError.NOT_A_BACKUP.takeIf { !File(staging, BackupContents.DATABASE).isFile }
        }

        private fun unpackEntry(entry: ZipEntry): BackupError? {
            if (entry.isDirectory || !contents.allows(entry.name)) {
                skipped++
                return null
            }
            count++
            val written = count <= config.maxEntries && File(staging, entry.name).let { target ->
                target.parentFile?.mkdirs()
                val limit = minOf(config.maxEntryBytes, config.maxTotalBytes - total)
                target.outputStream().use { out -> readBounded(out, limit) } != null
            }
            return if (written) null else BackupError.TOO_LARGE
        }

        /** Copies the current entry to [out]; null (and [out] left partial) if it passes [limit] bytes. */
        private fun <T : OutputStream> readBounded(out: T, limit: Long): T? {
            var copied = 0L
            var read = zip.read(buffer)
            while (read >= 0) {
                copied += read
                if (copied > limit) return null
                out.write(buffer, 0, read)
                read = zip.read(buffer)
            }
            total += copied
            return out
        }
    }

    private companion object {
        const val TAG = "Backup.Archive"
        const val MANIFEST_MAX_BYTES = 4_096L
    }
}
