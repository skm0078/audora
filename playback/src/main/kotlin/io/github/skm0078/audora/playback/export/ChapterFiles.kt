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
    /** The file's name, safe to show and log (it is built from page numbers, never the PDF's text). */
    val name: String

    val descriptor: ParcelFileDescriptor

    /** Closes the descriptor and makes the finished file visible to other apps. */
    fun publish(): Outcome<Unit, ExportError.Storage>

    /** Closes the descriptor and deletes the partial file. Never throws. */
    fun discard()
}

fun interface ChapterFiles {
    fun create(name: String): Outcome<ChapterFile, ExportError.Storage>
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
        override val name: String,
        private val uri: Uri,
        override val descriptor: ParcelFileDescriptor,
    ) : ChapterFile {
        override fun publish(): Outcome<Unit, ExportError.Storage> = try {
            descriptor.close()
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not finish $name", e)
            Outcome.Failure(ExportError.Storage("could not finish the file"))
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
