// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookExport.kt
// Purpose: The one call the UI makes to save a library book as an audiobook, and its Continue (ADR-034).

package io.github.skm0078.audora.playback.export

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog

object AudiobookExport {
    internal const val ACTION_START = "io.github.skm0078.audora.export.START"
    internal const val ACTION_CANCEL = "io.github.skm0078.audora.export.CANCEL"
    internal const val EXTRA_BOOK = "book"
    internal const val EXTRA_CONTINUE_BOOK = "io.github.skm0078.audora.export.CONTINUE_BOOK"
    private const val TAG = "Export.Start"

    /**
     * Starts the export service for [id]. Call it from a user action while Audora is on screen: Android lets an
     * app start a foreground service only then, and Audora never starts work on its own (C7). [password]: the one
     * the reader opened the book with, handed over in memory ([PasswordHandoff]); from the library it is null, and a
     * password-protected book is then refused with a message.
     */
    fun start(context: Context, id: BookId, password: String? = null) {
        AudoraLog.i(TAG, "Export requested, book=${id.logLabel}")
        val intent = Intent(context, AudiobookExportService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_BOOK, id.value)
        PasswordHandoff.shared.handOver(id, password) { context.startForegroundService(intent) }
    }

    /**
     * The "paused by Android's time limit" notification's Continue opens Audora with the export to go on with; call
     * this from the activity's onCreate and onNewIntent. Opening the app reset Android's limit, so the export starts
     * again and renders only the chapters not saved yet. True when [intent] carried such an export.
     */
    fun continueFrom(context: Context, intent: Intent?): Boolean {
        val id = intent?.getStringExtra(EXTRA_CONTINUE_BOOK)?.let(::BookId) ?: return false
        intent.removeExtra(EXTRA_CONTINUE_BOOK) // Once: a later recreation of the activity must not start it again.
        start(context, id)
        return true
    }
}
