// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookExport.kt
// Purpose: The one call the UI makes to save a library book as an audiobook (ADR-034).

package io.github.skm0078.audora.playback.export

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog

object AudiobookExport {
    internal const val ACTION_START = "io.github.skm0078.audora.export.START"
    internal const val ACTION_CANCEL = "io.github.skm0078.audora.export.CANCEL"
    internal const val EXTRA_BOOK = "book"

    /**
     * Starts the export service for [id]. Call it from a user action while Audora is on screen: Android lets an
     * app start a foreground service only then, and Audora never starts work on its own (C7).
     */
    fun start(context: Context, id: BookId) {
        AudoraLog.i(TAG, "Export requested, book=${id.logLabel}")
        val intent = Intent(context, AudiobookExportService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_BOOK, id.value)
        context.startForegroundService(intent)
    }

    private const val TAG = "Export.Start"
}
