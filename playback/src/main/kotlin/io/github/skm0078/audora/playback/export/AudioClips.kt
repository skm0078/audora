// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudioClips.kt
// Purpose: The one call the UI makes to share a passage as an audio clip (plan item 41).

package io.github.skm0078.audora.playback.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog

object AudioClips {
    internal const val ACTION_MAKE = "io.github.skm0078.audora.clip.MAKE"
    internal const val EXTRA_BOOK = "book"
    internal const val EXTRA_PAGE = "page"
    internal const val EXTRA_INDEX = "index"

    /** The only folder [ClipFileProvider] hands out (res/xml/clip_share_paths.xml). */
    internal const val FOLDER = "shared-clips"
    private const val AUTHORITY_SUFFIX = ".clips"
    private const val TAG = "Clip.Start"

    /**
     * Renders the passage from sentence [index] of [page] (zero-based) to the end of its paragraph, then opens the
     * share sheet. Call it from a user action while Audora is on screen: the work runs in a short foreground service
     * (Android allows starting one only then), and its notification carries a Share button in case the sheet cannot
     * open by itself.
     */
    fun share(context: Context, id: BookId, page: Int, index: Int) {
        AudoraLog.i(TAG, "Clip requested, book=${id.logLabel} page=$page sentence=$index")
        val intent = Intent(context, ClipService::class.java)
            .setAction(ACTION_MAKE)
            .putExtra(EXTRA_BOOK, id.value)
            .putExtra(EXTRA_PAGE, page)
            .putExtra(EXTRA_INDEX, index)
        context.startForegroundService(intent)
    }

    internal fun uriFor(context: Context, file: java.io.File) =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
}

/** Its own class, so its manifest entry does not collide with the app's FileProvider for logs. */
class ClipFileProvider : FileProvider()
