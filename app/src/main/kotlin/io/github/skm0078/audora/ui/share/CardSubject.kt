// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardSubject.kt
// Purpose: What a card is about, loaded once for the card screen: the book's name, author and covers, and what the
//          card shows (the book, a quote tidied and cut with its page in its own language, or part of a page).

package io.github.skm0078.audora.ui.share

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.share.CardQuote
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.export.ShareSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The loaded subject: the book's name, author and covers ([softCover] backs the Cover look), and what it shows. */
data class CardSubject(
    val title: String,
    val author: String?,
    val cover: ImageBitmap?,
    val softCover: ImageBitmap?,
    val body: CardBody,
) {
    /** The quote, for a quote card; null for a book or a page snippet. */
    val quote: CardQuote? get() = (body as? CardBody.Quote)?.quote

    /** The card can show its whole page instead of the selection (ADR 2026-10-30-whole-page-cards). */
    val hasWholePage: Boolean
        get() = when (body) {
            CardBody.WholeBook -> false
            is CardBody.Quote -> body.wholePage != null
            is CardBody.Snippet -> true
        }

    /** The book and page the caption beside the picture credits. */
    val source: ShareSource
        get() = ShareSource(
            title,
            author,
            when (body) {
                CardBody.WholeBook -> null
                is CardBody.Quote -> body.page
                is CardBody.Snippet -> body.page
            },
        )
}

/** What a card shows over its credit: one of the three, never a mix. */
sealed interface CardBody {
    /** The book itself: its cover, title and author. */
    data object WholeBook : CardBody

    /**
     * [quote] from zero-based [page] when it is known; [pageLabel] in the quote's own language. [wholePage] is the
     * page's own text, when the reader sent it, for "Whole page".
     */
    data class Quote(
        val quote: CardQuote,
        val page: Int?,
        val pageLabel: String?,
        val wholePage: CardQuote? = null,
    ) : CardBody

    /** Part of zero-based [page] as drawn ([PageSnippets]) and the [wholePage], with its page in the app's language. */
    data class Snippet(val picture: ImageBitmap, val page: Int, val pageLabel: String, val wholePage: ImageBitmap) :
        CardBody
}

/** Loading, loaded, or the book is gone (removed while a Share was on its way). */
sealed interface CardSubjectState {
    data object Loading : CardSubjectState

    data object Missing : CardSubjectState

    /** The page snippet is no longer in memory (Android stopped the app while the screen was away). */
    data object SnippetGone : CardSubjectState

    data class Ready(val subject: CardSubject) : CardSubjectState
}

internal object CardSubjects {
    private const val TAG = "ShareCard"

    suspend fun load(
        context: Context,
        request: ShareCardRequest,
        config: ShareCardConfig = ShareCardConfig(),
    ): CardSubjectState {
        val book = LibraryEntryPoint.of(context).library().book(request.book)
        val body = bodyOf(context, request, config)
        return when {
            book == null -> CardSubjectState.Missing.also {
                AudoraLog.w(TAG, "Book for the card is gone, book=${request.book.logLabel}")
            }

            body == null -> CardSubjectState.SnippetGone

            else -> CardSubjectState.Ready(subjectOf(context, book, body))
        }
    }

    /** What [request] shows; null for a snippet no longer held. */
    private fun bodyOf(context: Context, request: ShareCardRequest, config: ShareCardConfig): CardBody? =
        when (request) {
            is ShareCardRequest.WholeBook -> CardBody.WholeBook

            is ShareCardRequest.Quote -> CardQuote.of(request.lines, config).let { quote ->
                CardBody.Quote(
                    quote,
                    request.page,
                    request.page?.let { pageLabel(context, quote.script, it) },
                    request.pageText?.takeIf(String::isNotBlank)?.let { CardQuote.ofPage(it, config) },
                )
            }

            is ShareCardRequest.Snippet -> PageSnippets.current()?.let { held ->
                val label = context.getString(R.string.share_card_page, request.page + 1)
                CardBody.Snippet(held.part.asImageBitmap(), request.page, label, held.page.asImageBitmap())
            }
        }

    private suspend fun subjectOf(context: Context, book: Book, body: CardBody): CardSubject {
        val cover = LibraryEntryPoint.of(context).covers().cover(book)
        val soft = cover?.let { withContext(Dispatchers.Default) { CardPictures.softened(it) } }
        val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: context.getString(R.string.library_untitled)
        val author = book.author?.takeIf(String::isNotBlank)
        return CardSubject(title, author, cover, soft, body)
    }

    /** "Page 3", "पृष्ठ 3", "পৃষ্ঠা 3": the card speaks the quote's language, whatever the app's. */
    private fun pageLabel(context: Context, script: Lang, page: Int): String {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(script.bcp47Tag))
        }
        return context.createConfigurationContext(config).getString(R.string.share_card_page, page + 1)
    }
}
