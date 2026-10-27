// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareEntry.kt
// Purpose: The one call every Share button makes to open the share card screen: lines from the reader, or a book
//          from the library (ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.ui.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.text.TextCuts

/** What the card screen was opened for. */
sealed interface ShareCardRequest {
    val book: BookId

    /** [lines] the listener picked, on zero-based [page] when it is known. */
    data class Quote(override val book: BookId, val lines: String, val page: Int?) : ShareCardRequest

    data class WholeBook(override val book: BookId) : ShareCardRequest

    companion object {
        internal const val EXTRA_BOOK = "io.github.skm0078.audora.share.BOOK"
        internal const val EXTRA_LINES = "io.github.skm0078.audora.share.LINES"
        internal const val EXTRA_PAGE = "io.github.skm0078.audora.share.PAGE"
        private const val NO_PAGE = -1

        // A card shows 60 words; this much is plenty to cut them from and keeps the intent small.
        internal const val MAX_LINES_CHARS = 4_000

        fun from(intent: Intent): ShareCardRequest? {
            val book = intent.getStringExtra(EXTRA_BOOK)?.takeIf(String::isNotBlank)?.let(::BookId) ?: return null
            val lines = intent.getStringExtra(EXTRA_LINES)
            val page = intent.getIntExtra(EXTRA_PAGE, NO_PAGE).takeIf { it >= 0 }
            return if (lines == null) WholeBook(book) else Quote(book, lines, page)
        }

        internal fun Intent.put(request: ShareCardRequest): Intent = apply {
            putExtra(EXTRA_BOOK, request.book.value)
            if (request is Quote) {
                putExtra(EXTRA_LINES, TextCuts.take(request.lines, MAX_LINES_CHARS))
                putExtra(EXTRA_PAGE, request.page ?: NO_PAGE)
            }
        }
    }
}

/**
 * Called from the reader's sentence menu and the library's book menu (and, later, the reader's side panel), always
 * from a tap while ListenLock is on screen. Nothing is shared until the listener presses Share on the card screen.
 */
object ShareEntry {
    private const val TAG = "ShareCard"

    /** Lines from a book as a quote card; [page] is zero-based, as the reader counts. */
    fun quote(context: Context, book: BookId, lines: String, page: Int?) =
        open(context, ShareCardRequest.Quote(book, lines, page))

    /** The book itself: cover, title and author. */
    fun book(context: Context, book: BookId) = open(context, ShareCardRequest.WholeBook(book))

    private fun open(context: Context, request: ShareCardRequest) {
        AudoraLog.i(TAG, "Card screen opened for book=${request.book.logLabel} kind=${request.javaClass.simpleName}")
        val intent = with(ShareCardRequest) { Intent(context, ShareCardActivity::class.java).put(request) }
        // From an activity it opens on top of it; from anything else it needs a task of its own.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
