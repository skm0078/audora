// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareEntry.kt
// Purpose: The one call every Share button makes to open the share card screen: lines from the reader, or a book
//          from the library (ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.ui.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.text.TextCuts
import io.github.skm0078.audora.playback.export.ShareSource
import io.github.skm0078.audora.playback.export.ShareWords

/** What the card screen was opened for. */
sealed interface ShareCardRequest {
    val book: BookId

    /** [lines] the listener picked, on zero-based [page] when it is known; [video] opens the screen on Video. */
    data class Quote(override val book: BookId, val lines: String, val page: Int?, val video: Boolean = false) :
        ShareCardRequest

    data class WholeBook(override val book: BookId) : ShareCardRequest

    /** Part of zero-based [page] as drawn, held by [PageSnippets] (ADR 2026-10-28-page-snippet). */
    data class Snippet(override val book: BookId, val page: Int) : ShareCardRequest

    companion object {
        internal const val EXTRA_BOOK = "io.github.skm0078.audora.share.BOOK"
        internal const val EXTRA_LINES = "io.github.skm0078.audora.share.LINES"
        internal const val EXTRA_PAGE = "io.github.skm0078.audora.share.PAGE"
        internal const val EXTRA_VIDEO = "io.github.skm0078.audora.share.VIDEO"
        internal const val EXTRA_SNIPPET = "io.github.skm0078.audora.share.SNIPPET"
        private const val NO_PAGE = -1

        // A card shows 60 words; this much is plenty to cut them from and keeps the intent small.
        internal const val MAX_LINES_CHARS = 4_000

        fun from(intent: Intent): ShareCardRequest? {
            val book = intent.getStringExtra(EXTRA_BOOK)?.takeIf(String::isNotBlank)?.let(::BookId) ?: return null
            val lines = intent.getStringExtra(EXTRA_LINES)
            val page = intent.getIntExtra(EXTRA_PAGE, NO_PAGE).takeIf { it >= 0 }
            val video = intent.getBooleanExtra(EXTRA_VIDEO, false)
            return when {
                intent.getBooleanExtra(EXTRA_SNIPPET, false) -> Snippet(book, page ?: 0)
                lines == null -> WholeBook(book)
                else -> Quote(book, lines, page, video)
            }
        }

        internal fun Intent.put(request: ShareCardRequest): Intent = apply {
            putExtra(EXTRA_BOOK, request.book.value)
            if (request is Quote) {
                putExtra(EXTRA_LINES, TextCuts.take(request.lines, MAX_LINES_CHARS))
                putExtra(EXTRA_PAGE, request.page ?: NO_PAGE)
                putExtra(EXTRA_VIDEO, request.video)
            }
            if (request is Snippet) {
                putExtra(EXTRA_SNIPPET, true)
                putExtra(EXTRA_PAGE, request.page)
            }
        }
    }
}

/**
 * Called from the reader's Share button and sentence menu and the library's book menu, always
 * from a tap while ListenLock is on screen. Nothing is shared until the listener presses Share on the card screen.
 */
object ShareEntry {
    private const val TAG = "ShareCard"
    private const val PLAIN_TEXT = "text/plain"

    /** Lines from a book as a quote card; [page] is zero-based, as the reader counts. */
    fun quote(context: Context, book: BookId, lines: String, page: Int?) =
        open(context, ShareCardRequest.Quote(book, lines, page))

    /** The same lines as a quote video: the card screen opens on Video, read aloud in the book's voice. */
    fun video(context: Context, book: BookId, lines: String, page: Int?) =
        open(context, ShareCardRequest.Quote(book, lines, page, video = true))

    /** Part of a drawn page, cut by [PageSnippets.hold] just before; [page] is zero-based. */
    fun snippet(context: Context, book: BookId, page: Int) = open(context, ShareCardRequest.Snippet(book, page))

    /** The book itself: cover, title and author. */
    fun book(context: Context, book: BookId) = open(context, ShareCardRequest.WholeBook(book))

    /**
     * [lines] as plain text, in quotes and cut to the text limit, with [source]'s credit and ListenLock's link (ADR
     * 2026-10-29-share-credit-and-link), straight to the app the listener picks.
     */
    fun text(context: Context, source: ShareSource, lines: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(PLAIN_TEXT)
            .putExtra(Intent.EXTRA_TEXT, ShareWords.caption(context, source, lines))
        val chooser = Intent.createChooser(send, context.getString(R.string.sentence_menu_share_text))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
            AudoraLog.i(TAG, "Text share opened, page=${source.page}")
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(TAG, "No app to share text with: ${e.javaClass.simpleName}")
        }
    }

    private fun open(context: Context, request: ShareCardRequest) {
        AudoraLog.i(TAG, "Card screen opened for book=${request.book.logLabel} kind=${request.javaClass.simpleName}")
        val intent = with(ShareCardRequest) { Intent(context, ShareCardActivity::class.java).put(request) }
        // From an activity it opens on top of it; from anything else it needs a task of its own.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
