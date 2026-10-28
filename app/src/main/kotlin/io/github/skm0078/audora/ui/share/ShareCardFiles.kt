// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardFiles.kt
// Purpose: Writes a captured card into the app's own share folder and hands it to the app the listener picks.

package io.github.skm0078.audora.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** A card written for sharing: the file and its type. */
data class CardFile(val file: File, val mimeType: String)

/**
 * cacheDir/shared-cards only (res/xml/share_card_paths.xml): no storage permission, nothing in the gallery, and the
 * phone may clear it. The [ShareCardConfig.keptCards] newest stay so a chat app that reads the file late still finds
 * it; older ones are deleted on each write. Blocking: call off the main thread.
 */
internal class ShareCardFiles(context: Context, private val config: ShareCardConfig = ShareCardConfig()) {
    private val app = context.applicationContext
    val folder = File(app.cacheDir, FOLDER)

    /** PNG keeps text sharp on flat colours; a photo background is JPEG, a tenth of the size. Null if not written. */
    fun write(card: Bitmap, photo: Boolean, nowMillis: Long = System.currentTimeMillis()): CardFile? {
        if (!folder.isDirectory && !folder.mkdirs()) {
            AudoraLog.e(TAG, "Share folder could not be made")
            return null
        }
        val (format, extension, type) = if (photo) JPEG else PNG
        val file = File(folder, "listenlock-$nowMillis.$extension")
        val partial = File(folder, "${file.name}$PARTIAL")
        val written = try {
            FileOutputStream(partial).use { card.compress(format, if (photo) JPEG_QUALITY else PNG_QUALITY, it) } &&
                partial.renameTo(file)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Card not written: ${e.javaClass.simpleName}", e)
            false
        }
        partial.delete()
        prune()
        AudoraLog.i(TAG, "Card written=$written ${card.width}x${card.height} $extension bytes=${file.length()}")
        return if (written) CardFile(file, type) else null
    }

    fun uri(card: CardFile): Uri = FileProvider.getUriForFile(app, "${app.packageName}$AUTHORITY_SUFFIX", card.file)

    private fun prune() {
        val cards = folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        cards.drop(config.keptCards).forEach { it.delete() }
    }

    companion object {
        /** The only folder [ShareCardFileProvider] hands out. */
        const val FOLDER = "shared-cards"
        private const val AUTHORITY_SUFFIX = ".cards"
        private const val PARTIAL = ".partial"
        private const val TAG = "ShareCard"
        private const val PNG_QUALITY = 100
        private const val JPEG_QUALITY = 92
        private val PNG = Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
        private val JPEG = Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
    }
}

/** Android's share sheet with the card; false when no app on the phone takes a picture. */
internal fun Context.sendCard(uri: Uri, mimeType: String): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData lets the share sheet preview the picture and carries the read grant to the chosen app.
    send.clipData = ClipData.newRawUri(null, uri)
    return try {
        startActivity(Intent.createChooser(send, getString(R.string.share_card_send)))
        true
    } catch (e: ActivityNotFoundException) {
        AudoraLog.w("ShareCard", "No app takes a picture: ${e.javaClass.simpleName}")
        false
    }
}

/** Its own class, so its manifest entry does not collide with the other FileProviders (logs, clips, photos). */
class ShareCardFileProvider : FileProvider()
