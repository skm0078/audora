// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/BookCopies.kt
// Purpose: The one call the UI makes to save an accessible copy or a talking book of a library book (items 69, 76).

package io.github.skm0078.audora.playback.epub

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.jobs.ContinueTokens

/** What a copy holds. */
enum class CopyKind {
    /** Clean text as EPUB 3 and plain text, for screen readers, braille displays and other apps (item 69). */
    ACCESSIBLE,

    /** EPUB 3 with the audio of every sentence synced to its text (item 76). */
    TALKING_BOOK,
}

object BookCopies {
    internal const val ACTION_START = "io.github.skm0078.audora.copy.START"
    internal const val ACTION_CANCEL = "io.github.skm0078.audora.copy.CANCEL"
    internal const val EXTRA_BOOK = "book"
    internal const val EXTRA_KIND = "kind"
    internal const val EXTRA_CONTINUE_BOOK = "io.github.skm0078.audora.copy.CONTINUE_BOOK"
    internal const val EXTRA_CONTINUE_KIND = "io.github.skm0078.audora.copy.CONTINUE_KIND"
    private const val TAG = "Copy.Start"

    /**
     * Starts the copy service for [id]. Call it from a user action while Audora is on screen: Android lets an app
     * start a foreground service only then, and Audora never starts work on its own (C7).
     */
    fun start(context: Context, id: BookId, kind: CopyKind) {
        AudoraLog.i(TAG, "Copy requested, book=${id.logLabel} kind=$kind")
        val intent = Intent(context, BookCopyService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_BOOK, id.value)
            .putExtra(EXTRA_KIND, kind.name)
        context.startForegroundService(intent)
    }

    /**
     * The "stopped by Android's time limit" notification's Continue opens Audora with the copy to go on with; call
     * this from the activity's onCreate and onNewIntent. Opening the app reset Android's limit, so the copy starts
     * again and renders only what was not staged. True when [intent] carried such a copy.
     */
    fun continueFrom(context: Context, intent: Intent?): Boolean {
        val id = intent?.getStringExtra(EXTRA_CONTINUE_BOOK)?.let(::BookId)
        val kind = intent?.getStringExtra(EXTRA_CONTINUE_KIND)?.let { name ->
            CopyKind.entries.find { it.name == name }
        }
        val token = intent?.getStringExtra(ContinueTokens.EXTRA_TOKEN)
        intent?.removeExtra(EXTRA_CONTINUE_BOOK) // Once: a later recreation of the activity must not start it again.
        if (id == null || kind == null) return false
        // Only Audora's own notification holds the token: another app opening Audora with a book id starts nothing.
        val ours = ContinueTokens.forContext(context).redeem(jobOf(id), token)
        if (ours) {
            start(context, id, kind)
        } else {
            AudoraLog.w(TAG, "Continue refused, not from Audora, book=${id.logLabel}")
        }
        return ours
    }

    /** The key of [id]'s Continue token ([ContinueTokens]). */
    internal fun jobOf(id: BookId): String = "copy:${id.value}"
}
