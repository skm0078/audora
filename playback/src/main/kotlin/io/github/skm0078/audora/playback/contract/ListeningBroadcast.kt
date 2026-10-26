// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/ListeningBroadcast.kt
// Purpose: The service tells the app's home-screen widget how long a book was just listened to (CF-7).

package io.github.skm0078.audora.playback.contract

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.playback.controls.ListenedSpan

/**
 * Sent only to Audora's own package (and received by a receiver no other app can reach), since the book id says what
 * is being read.
 */
object ListeningBroadcast {
    const val ACTION = "io.github.skm0078.audora.action.LISTENED"
    private const val EXTRA_BOOK_ID = "io.github.skm0078.audora.extra.LISTENED_BOOK_ID"
    private const val EXTRA_FROM = "io.github.skm0078.audora.extra.LISTENED_FROM"
    private const val EXTRA_TO = "io.github.skm0078.audora.extra.LISTENED_TO"

    fun intent(context: Context, bookId: String, span: ListenedSpan): Intent = Intent(ACTION)
        .setPackage(context.packageName)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .putExtra(EXTRA_FROM, span.fromElapsedMs)
        .putExtra(EXTRA_TO, span.toElapsedMs)

    /** The book and span [intent] carries; null for any other intent or a malformed one. */
    fun read(intent: Intent?): Pair<String, ListenedSpan>? {
        val ours = intent?.takeIf { it.action == ACTION } ?: return null
        val bookId = ours.getStringExtra(EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() }
        val from = ours.getLongExtra(EXTRA_FROM, -1)
        val to = ours.getLongExtra(EXTRA_TO, -1)
        return if (bookId != null && from >= 0 && to >= from) bookId to ListenedSpan(from, to) else null
    }
}
