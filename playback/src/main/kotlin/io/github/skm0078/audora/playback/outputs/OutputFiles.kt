// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/outputs/OutputFiles.kt
// Purpose: Asks shared storage about the files the app made (still there? how large?) and deletes them (ADR-068).

package io.github.skm0078.audora.playback.outputs

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.github.skm0078.audora.core.logging.AudoraLog

/** What shared storage says about one made file now. */
data class FileState(val present: Boolean, val sizeBytes: Long?)

/** How a delete went: [deleted] files are gone; [needsConsent] asks the listener for the rest (another install's). */
data class DeleteResult(val deleted: Int, val needsConsent: PendingIntent?)

/**
 * Blocking; call off the main thread. A file the app made can be deleted without asking while this install made it;
 * after a reinstall Android asks the listener first, through [DeleteResult.needsConsent]. Locations are never logged.
 */
class OutputFiles(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    /** One query per file: a file deleted in the Files app shows as gone, never as playable. */
    fun state(location: String): FileState = try {
        resolver.query(Uri.parse(location), arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                FileState(present = true, sizeBytes = cursor.getLong(0).takeIf { !cursor.isNull(0) })
            } else {
                GONE
            }
        } ?: GONE
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Made file unreadable: ${e.javaClass.simpleName}")
        GONE
    } catch (e: IllegalArgumentException) {
        AudoraLog.w(TAG, "Made file location not understood: ${e.javaClass.simpleName}")
        GONE
    }

    /** Deletes what it may; a file it may not delete goes to one consent request for all of them. */
    fun delete(locations: List<String>): DeleteResult {
        var deleted = 0
        val refused = ArrayList<Uri>()
        locations.map(Uri::parse).forEach { uri ->
            try {
                if (resolver.delete(uri, null, null) > 0) deleted++
            } catch (e: SecurityException) {
                AudoraLog.i(TAG, "Made file needs the listener's consent to delete: ${e.javaClass.simpleName}")
                refused += uri
            }
        }
        AudoraLog.i(TAG, "Made files deleted=$deleted refused=${refused.size} of ${locations.size}")
        val consent = refused.takeIf { it.isNotEmpty() }?.let { MediaStore.createDeleteRequest(resolver, it) }
        return DeleteResult(deleted, consent)
    }

    private companion object {
        const val TAG = "Library.Files"
        val GONE = FileState(present = false, sizeBytes = null)
    }
}
