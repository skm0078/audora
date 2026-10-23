// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/BackupArchive.kt
// Purpose: Writes a backup zip and unpacks a picked one into a staging folder, refusing anything unsafe (item 28).

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.config.BackupConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The first entry of every backup: which format and database version wrote it, [units], the allow-list of the
 * app that wrote it ([BackupContents.units]; null if not recorded), so a restore can tell a file the writer had not
 * got (reset) from one it did not know about (kept), and [bytes], what its entries unpack to (null if not recorded),
 * so a restore that would not fit is refused before anything is unpacked.
 */
data class BackupManifest(
    val format: Int,
    val databaseVersion: Int,
    val createdAtMs: Long,
    val units: List<String>? = null,
    val bytes: Long? = null,
) {
    fun encode(): ByteArray = Properties().apply {
        setProperty(FORMAT, format.toString())
        setProperty(DATABASE_VERSION, databaseVersion.toString())
        setProperty(CREATED_AT, createdAtMs.toString())
        units?.let { setProperty(UNITS, it.joinToString(UNIT_SEPARATOR)) }
        bytes?.let { setProperty(BYTES, it.toString()) }
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
        private const val UNITS = "units"
        private const val BYTES = "bytes"
        private const val UNIT_SEPARATOR = ","

        /** Null unless all three numeric fields are there and numeric; throws IllegalArgumentException if malformed. */
        fun decode(bytes: ByteArray): BackupManifest? {
            val properties = Properties().apply { load(bytes.inputStream()) }
            val format = properties.getProperty(FORMAT)?.toIntOrNull()
            val version = properties.getProperty(DATABASE_VERSION)?.toIntOrNull()
            val created = properties.getProperty(CREATED_AT)?.toLongOrNull()
            return if (format != null && version != null && created != null) {
                val units = properties.getProperty(UNITS)?.split(UNIT_SEPARATOR)?.filter { it.isNotEmpty() }
                BackupManifest(format, version, created, units, properties.getProperty(BYTES)?.toLongOrNull())
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

    /** The phone has too little free space to unpack it. */
    NO_SPACE,

    /** An export, copy, clip or studio run is going; restoring would restart the app under it. */
    BUSY,

    /** An earlier restore failed and is still being undone at each start; nothing new can be restored meanwhile. */
    UNFINISHED,

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
     * Writes the manifest (with the entries' total size), the [database] snapshot as [BackupContents.DATABASE] and
     * every allowed file under [root] to [out], which the caller closes. Throws IOException; the caller deletes the
     * half-written file.
     */
    fun write(out: OutputStream, manifest: BackupManifest, database: File, root: File): BackupSummary {
        val entries = listOf(BackupContents.DATABASE to database) + contents.existing(root)
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
        zip.write(manifest.copy(bytes = entries.sumOf { it.second.length() }).encode())
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
        } catch (e: IllegalArgumentException) {
            // A malformed entry name (ZipInputStream) or a bad escape in the manifest (Properties).
            AudoraLog.w(TAG, "Backup malformed: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.NOT_A_BACKUP)
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Backup not unpacked: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.IO_FAILED)
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Backup not unpacked: ${e.javaClass.simpleName}")
            Outcome.Failure(BackupError.IO_FAILED)
        }
        if (result is Outcome.Failure) staging.deleteRecursively()
        return result
    }

    /** One unpacking pass; throws IOException for a read or write failure, IllegalArgumentException if malformed. */
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
                manifest.bytes?.let { !fits(it) } == true -> BackupError.NO_SPACE
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
            val out = ByteArrayOutputStream()
            val read = copy(out, MANIFEST_MAX_BYTES, checkSpace = false)
            return if (read == Copy.DONE) BackupManifest.decode(out.toByteArray()) else null
        }

        /** Whether [bytes] more still leave [BackupConfig.minFreeBytes] free where staging unpacks. */
        private fun fits(bytes: Long): Boolean {
            staging.mkdirs()
            return staging.usableSpace - bytes >= config.minFreeBytes
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
            val name = contents.current(entry.name)
            return when {
                entry.isDirectory || !contents.allows(name) -> {
                    skipped++
                    null
                }

                ++count > config.maxEntries -> BackupError.TOO_LARGE

                else -> unpackTo(File(staging, name))
            }
        }

        private fun unpackTo(target: File): BackupError? {
            target.parentFile?.mkdirs()
            val limit = minOf(config.maxEntryBytes, config.maxTotalBytes - total)
            // Synced as written: the marker that makes this restore apply is written after, and must never point at
            // files a power cut emptied.
            val copied = FileOutputStream(target).use { out -> copy(out, limit).also { out.fd.sync() } }
            return when (copied) {
                Copy.DONE -> null
                Copy.TOO_LARGE -> BackupError.TOO_LARGE
                Copy.NO_SPACE -> BackupError.NO_SPACE
            }
        }

        /**
         * Copies the current entry to [out], counting the bytes actually read (an entry's claimed size is not
         * trusted, and streamed entries claim none): stops past [limit] or, with [checkSpace], when free space drops
         * below the minimum, checked every [BackupConfig.spaceCheckBytes].
         */
        private fun copy(out: OutputStream, limit: Long, checkSpace: Boolean = true): Copy {
            var copied = 0L
            var sinceCheck = 0L
            var result = Copy.DONE
            var read = zip.read(buffer)
            while (read >= 0 && result == Copy.DONE) {
                copied += read
                sinceCheck += read
                val checkNow = checkSpace && sinceCheck >= config.spaceCheckBytes
                if (checkNow) sinceCheck = 0
                result = when {
                    copied > limit -> Copy.TOO_LARGE
                    checkNow && !fits(0) -> Copy.NO_SPACE
                    else -> Copy.DONE
                }
                if (result == Copy.DONE) {
                    out.write(buffer, 0, read)
                    read = zip.read(buffer)
                }
            }
            if (result == Copy.DONE) total += copied
            return result
        }
    }

    private enum class Copy { DONE, TOO_LARGE, NO_SPACE }

    private companion object {
        const val TAG = "Backup.Archive"
        const val MANIFEST_MAX_BYTES = 4_096L
    }
}
