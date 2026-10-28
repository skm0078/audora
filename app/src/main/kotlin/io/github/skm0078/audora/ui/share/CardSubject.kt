// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardSubject.kt
// Purpose: What a card is about, loaded once for the card screen: the book's name, author and covers, and the quote
//          tidied and cut, with its page in the quote's own language.

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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The loaded subject; [quote] is null for a whole-book card or a [snippet] (part of a page). [softCover] backs the
 * Cover look.
 */
data class CardSubject(
    val title: String,
    val author: String?,
    val cover: ImageBitmap?,
    val softCover: ImageBitmap?,
    val quote: CardQuote?,
    val pageLabel: String?,
    val snippet: ImageBitmap? = null,
)

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
        val entry = LibraryEntryPoint.of(context)
        val book = entry.library().book(request.book)
        val snippet = PageSnippets.current()?.takeIf { request is ShareCardRequest.Snippet }?.asImageBitmap()
        return when {
            book == null -> CardSubjectState.Missing.also {
                AudoraLog.w(TAG, "Book for the card is gone, book=${request.book.logLabel}")
            }

            request is ShareCardRequest.Snippet && snippet == null -> CardSubjectState.SnippetGone

            else -> CardSubjectState.Ready(subjectOf(context, book, request, snippet, config))
        }
    }

    private suspend fun subjectOf(
        context: Context,
        book: Book,
        request: ShareCardRequest,
        snippet: ImageBitmap?,
        config: ShareCardConfig,
    ): CardSubject {
        val entry = LibraryEntryPoint.of(context)
        val cover = entry.covers().cover(book)
        val soft = cover?.let { withContext(Dispatchers.Default) { CardPictures.softened(it) } }
        val quote = (request as? ShareCardRequest.Quote)?.let { CardQuote.of(it.lines, config) }
        val page = (request as? ShareCardRequest.Quote)?.page
        val pageLabel = when {
            quote != null && page != null -> pageLabel(context, quote.script, page)
            request is ShareCardRequest.Snippet -> context.getString(R.string.share_card_page, request.page + 1)
            else -> null
        }
        val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: context.getString(R.string.library_untitled)
        val author = book.author?.takeIf(String::isNotBlank)
        return CardSubject(title, author, cover, soft, quote, pageLabel, snippet)
    }

    /** "Page 3", "पृष्ठ 3", "পৃষ্ঠা 3": the card speaks the quote's language, whatever the app's. */
    private fun pageLabel(context: Context, script: Lang, page: Int): String {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(script.bcp47Tag))
        }
        return context.createConfigurationContext(config).getString(R.string.share_card_page, page + 1)
    }
}
