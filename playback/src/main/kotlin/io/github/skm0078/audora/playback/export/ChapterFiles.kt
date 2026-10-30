// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ChapterFiles.kt
// Purpose: Where chapter files are created: the shared Audiobooks folder (MediaStore) or a plain folder (tests).

package io.github.skm0078.audora.playback.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** One chapter's file while it is written: a seekable descriptor (MediaMuxer rewrites the header at the end). */
interface ChapterFile {
    /**
     * The file's name, safe to show and log (it is built from page numbers, never the PDF's text). After [publish],
     * the name it was saved under.
     */
    val name: String

    /** Where the file can be found again after [publish] (a MediaStore URI), or null; never logged. */
    val location: String? get() = null

    val descriptor: ParcelFileDescriptor

    /** Closes the descriptor and makes the finished file visible to other apps. */
    fun publish(): Outcome<Unit, ExportError.Storage>

    /** Closes the descriptor and deletes the partial file. Never throws. */
    fun discard()
}

fun interface ChapterFiles {
    fun create(name: String): Outcome<ChapterFile, ExportError.Storage>

    /** These files with each one recorded in [session] as pending once created, before anything is written. */
    fun recordedIn(session: ExportSession) = ChapterFiles { name ->
        create(name).also { created -> (created as? Outcome.Success)?.value?.location?.let(session::onFileCreated) }
    }
}

/**
 * Files in `Audiobooks/<folder>/` on shared storage, where audiobook players look. MediaStore needs no storage
 * permission for files the app creates itself (API 29+). Each file stays pending, hidden from other apps, until
 * it is complete, so a player never picks up half a chapter.
 */
class MediaStoreChapterFiles(private val resolver: ContentResolver, private val relativePath: String) : ChapterFiles {
    override fun create(name: String): Outcome<ChapterFile, ExportError.Storage> {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_AUDIOBOOK, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = insert(name, values) ?: return Outcome.Failure(ExportError.Storage("shared storage refused the file"))
        return when (val descriptor = openForWriting(name, uri)) {
            null -> {
                resolver.delete(uri, null, null)
                Outcome.Failure(ExportError.Storage("could not open the file for writing"))
            }

            else -> Outcome.Success(Entry(name, uri, descriptor))
        }
    }

    private fun insert(name: String, values: ContentValues): Uri? = try {
        resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    } catch (e: IllegalArgumentException) {
        AudoraLog.e(TAG, "MediaStore refused $name in $relativePath", e)
        null
    }

    private fun openForWriting(name: String, uri: Uri): ParcelFileDescriptor? = try {
        resolver.openFileDescriptor(uri, MODE)
    } catch (e: FileNotFoundException) {
        AudoraLog.e(TAG, "Could not open $name for writing", e)
        null
    }

    private inner class Entry(
        override var name: String,
        private val uri: Uri,
        override val descriptor: ParcelFileDescriptor,
    ) : ChapterFile {
        override val location: String get() = uri.toString()

        override fun publish(): Outcome<Unit, ExportError.Storage> = try {
            descriptor.close()
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            savedName()?.let { name = it }
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not finish $name", e)
            Outcome.Failure(ExportError.Storage("could not finish the file"))
        }

        /** The name shared storage gave the file, which differs from the one asked for when that was taken. */
        private fun savedName(): String? = try {
            resolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
                cursor.takeIf { it.moveToFirst() }?.getString(0)
            }
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Saved name of $name unreadable: ${e.javaClass.simpleName}")
            null
        }

        override fun discard() {
            closeQuietly(descriptor, name)
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                AudoraLog.w(TAG, "Could not delete the partial $name", e)
            }
        }
    }

    private companion object {
        const val TAG = "Export.Files"
        const val MIME_TYPE = "audio/mp4"
        const val MODE = "rw"
    }
}

/**
 * An earlier export's files in shared storage, by their MediaStore URIs. The app may delete files it created itself;
 * files left by an earlier install of Audora belong to no app it can speak for, so their deletion fails (logged) and
 * they stay beside the new ones.
 */
class MediaStoreSavedFiles(private val resolver: ContentResolver) : SavedFiles {
    override fun exists(location: String): Boolean = try {
        resolver.query(Uri.parse(location), arrayOf(MediaStore.Audio.Media._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Earlier chapter file unreadable: ${e.javaClass.simpleName}")
        false
    }

    override fun sizeOf(location: String): Long = try {
        resolver.query(Uri.parse(location), arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Earlier chapter file size unreadable: ${e.javaClass.simpleName}")
        0L
    }

    override fun delete(location: String): Boolean = try {
        resolver.delete(Uri.parse(location), null, null) > 0 || !exists(location)
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Earlier chapter file not deleted: ${e.javaClass.simpleName}")
        false
    }

    private companion object {
        const val TAG = "Export.Files"
    }
}

/** Plain files in [folder]: for tests and debugging; other apps cannot see the app's private folders. */
class LocalChapterFiles(private val folder: File) : ChapterFiles {
    override fun create(name: String): Outcome<ChapterFile, ExportError.Storage> {
        if (!folder.isDirectory) return Outcome.Failure(ExportError.Storage("the folder is missing"))
        val file = File(folder, name)
        val mode = ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE
        return try {
            Outcome.Success(Entry(file, ParcelFileDescriptor.open(file, mode)))
        } catch (e: FileNotFoundException) {
            AudoraLog.e(TAG, "Could not create $name", e)
            Outcome.Failure(ExportError.Storage("could not create the file"))
        }
    }

    private class Entry(private val file: File, override val descriptor: ParcelFileDescriptor) : ChapterFile {
        override val name: String get() = file.name

        override fun publish(): Outcome<Unit, ExportError.Storage> = try {
            descriptor.close()
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not close $name", e)
            Outcome.Failure(ExportError.Storage("could not finish the file"))
        }

        override fun discard() {
            closeQuietly(descriptor, name)
            if (file.exists() && !file.delete()) AudoraLog.w(TAG, "Could not delete the partial $name")
        }
    }

    private companion object {
        const val TAG = "Export.Files"
    }
}

private fun closeQuietly(descriptor: ParcelFileDescriptor, name: String) {
    try {
        descriptor.close()
    } catch (e: IOException) {
        AudoraLog.w("Export.Files", "Could not close $name", e)
    }
}
