// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/SharedDocuments.kt
// Purpose: Writes a copy into Documents/ListenLock/ on shared storage, hidden from other apps until it is complete.

package io.github.skm0078.audora.playback.epub

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * MediaStore needs no storage permission for files the app creates itself (API 29+); ebook readers, file managers and
 * screen-reader apps find them in Documents. A name that is taken gets " (1)" from Android, so an older copy is kept.
 */
internal class SharedDocuments(private val resolver: ContentResolver, private val relativePath: String) {
    /** A file written: [name] as saved (safe to show), [location] its MediaStore URI (never logged), its size. */
    data class Saved(val name: String, val location: String, val sizeBytes: Long, val mimeType: String)

    /**
     * Writes [name] through [write]; the file appears only when [write] returns normally. On any failure, and on
     * cancel (the [InterruptedException] is rethrown), the partial file is deleted. Returns the name the file got
     * (which is "[name] (1)…" when an older copy has the name) and where it is.
     */
    fun save(name: String, mimeType: String, write: (OutputStream) -> Unit): Outcome<Saved, CopyError> {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = insert(name, values) ?: return Outcome.Failure(CopyError.Storage("shared storage refused the file"))
        var done = false
        return try {
            val stream = CountingStream(resolver.openOutputStream(uri) ?: throw IOException("no stream for $name"))
            stream.use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            done = true
            Outcome.Success(Saved(displayName(uri) ?: name, uri.toString(), stream.count, mimeType))
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not write $name", e)
            Outcome.Failure(CopyError.Storage("could not write the file"))
        } finally {
            if (!done) delete(uri, name)
        }
    }

    private fun insert(name: String, values: ContentValues): Uri? = try {
        resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    } catch (e: IllegalArgumentException) {
        AudoraLog.e(TAG, "MediaStore refused $name in $relativePath", e)
        null
    }

    /** The name MediaStore gave the file; null (the asked-for name is shown) if it cannot be read back. */
    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        }
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Saved file's name unreadable: ${e.javaClass.simpleName}")
        null
    }

    private fun delete(uri: Uri, name: String) {
        try {
            resolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Could not delete the partial $name", e)
        }
    }

    private companion object {
        const val TAG = "Copy.Files"
    }
}

/** Counts what goes through, so a saved file's size is known without asking MediaStore again. */
private class CountingStream(private val out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
