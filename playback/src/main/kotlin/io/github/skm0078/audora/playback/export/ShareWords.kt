// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ShareWords.kt
// Purpose: The caption beside every share, in the app's language: a clip and a quote video here, and the app's
//          pictures and text through the same call (ADR 2026-10-29-share-credit-and-link).

package io.github.skm0078.audora.playback.export

import android.content.Context
import io.github.skm0078.audora.core.config.ShareLinkConfig
import io.github.skm0078.audora.core.share.ShareCaption
import io.github.skm0078.audora.playback.R

/** The book a share is from: its shown title (never blank), its author if known, and the zero-based page if any. */
data class ShareSource(val title: String, val author: String?, val page: Int?)

object ShareWords {
    /** "Title, Author, page 4": the parts that are known, page counted from one. */
    fun credit(context: Context, source: ShareSource): String {
        val author = source.author?.takeIf(String::isNotBlank)
        val page = source.page?.plus(1)
        return when {
            author != null && page != null ->
                context.getString(R.string.share_credit_author_page, source.title, author, page)

            author != null -> context.getString(R.string.share_credit_author, source.title, author)
            page != null -> context.getString(R.string.share_credit_page, source.title, page)
            else -> source.title
        }
    }

    /** [lines] in quotes when given (cut to the text limit), the credit when [source] is given, then the link. */
    fun caption(
        context: Context,
        source: ShareSource?,
        lines: String? = null,
        link: ShareLinkConfig = ShareLinkConfig(),
    ): String = ShareCaption.of(
        lines = lines,
        credit = source?.let { credit(context, it) },
        invite = context.getString(R.string.share_invite),
        link = link.appLink,
    )
}
