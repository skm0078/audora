// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ShareSheet.kt
// Purpose: The share sheet a finished clip or quote video opens on: the file through the clip FileProvider, with a
//          read grant for the app the listener picks.

package io.github.skm0078.audora.playback.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

internal object ShareSheet {
    /** A chooser titled [title] that sends [file] as [mimeType] with [text] (the words, the book) beside it. */
    fun chooserFor(context: Context, file: File, mimeType: String, text: String, title: String): Intent {
        val uri = AudioClips.uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData carries the grant through the chooser to the app the user picks.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /**
     * Opens [chooser] at once while ListenLock is on screen; Android blocks it from the background, and the caller's
     * notification offers it instead.
     */
    fun open(context: Context, chooser: Intent, tag: String) {
        try {
            context.startActivity(Intent(chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(tag, "No app to share with", e)
        }
    }
}
