// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsPictures.kt
// Purpose: "Save picture" on the stats card: the card as a PNG in the phone's Pictures/ListenLock, with no permission.

package io.github.skm0078.audora.ui.stats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * Android 10 and later let an app add its own pictures to the shared collection without asking (minSdk is 31). The
 * entry stays hidden (IS_PENDING) until the PNG is whole, and is removed if writing fails, so the gallery never shows
 * half a picture. Blocking: call off the main thread.
 */
internal object StatsPictures {
    fun save(context: Context, card: Bitmap, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "listenlock-stats-$nowMillis.png")
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // A phone without shared storage mounted refuses the entry; that is a failed save, said to the listener.
        val uri = try {
            resolver.insert(collection, details)
        } catch (e: IllegalArgumentException) {
            AudoraLog.w(TAG, "Stats picture not saved: ${e.javaClass.simpleName}")
            null
        }
        if (uri == null) {
            AudoraLog.w(TAG, "Stats picture not saved: no gallery entry")
            return false
        }
        val written = write(context, uri, card)
        if (written) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        AudoraLog.i(TAG, "Stats picture saved=$written ${card.width}x${card.height}")
        return written
    }

    private fun write(context: Context, uri: Uri, card: Bitmap): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use {
            card.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        } ==
            true
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Stats picture not written: ${e.javaClass.simpleName}")
        false
    }

    /** The gallery album: the brand, as on every shared picture. */
    private const val FOLDER = "ListenLock"
    private const val MIME_TYPE = "image/png"
    private const val PNG_QUALITY = 100
    private const val TAG = "Audora.StatsPicture"
}
