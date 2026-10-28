// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/VideoFiles.kt
// Purpose: The quote video's files in its share folder: the card screen's backdrop read at the video's size, and
//          old videos cleared.

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

internal object VideoFiles {
    private const val TAG = "Video.Files"
    private const val VIDEO_EXTENSION = "mp4"

    /** [file] (the card's 1080 x 1920) at [config]'s size; null if it is missing or unreadable. */
    fun backdropAt(file: File, config: QuoteVideoConfig): Bitmap? =
        BitmapFactory.decodeFile(file.path)?.let { decoded ->
            if (decoded.width == config.widthPx && decoded.height == config.heightPx) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, config.widthPx, config.heightPx, true).also { scaled ->
                    if (scaled !== decoded) decoded.recycle()
                }
            }
        }

    /** Videos beyond the [kept] newest, never [keep]; the backdrop (not a video) stays for the next one. */
    fun prune(folder: File, keep: File, kept: Int) {
        val videos = folder.listFiles { file -> file.extension == VIDEO_EXTENSION }.orEmpty()
            .sortedByDescending { it.lastModified() }
        videos.drop(kept).filter { it != keep }.forEach { old ->
            if (!old.delete()) AudoraLog.w(TAG, "Could not delete old video ${old.name}")
        }
    }
}
