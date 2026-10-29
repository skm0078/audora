// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/ShareCaption.kt
// Purpose: The words that go with every share (ADR 2026-10-29-share-credit-and-link): the lines when shared as text,
//          their credit, and an invitation with ListenLock's link, in that order.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig

/**
 * Pure. The caller passes each piece already in the listener's language; this decides only what goes in and in which
 * order, so a picture, a clip, a video and plain text all read the same way in a chat.
 */
object ShareCaption {
    /**
     * [lines], when given, cut to [ShareCardConfig.maxTextWords] at a sentence end when one is close and set in
     * quotes; then "— [credit]" when given; then [invite] and [link] on the last line, where chat apps make it a
     * link.
     */
    fun of(
        lines: String?,
        credit: String?,
        invite: String,
        link: String,
        config: ShareCardConfig = ShareCardConfig(),
    ): String {
        val quote = lines?.takeIf(String::isNotBlank)
            ?.let { CardQuote.of(it, config.copy(maxWords = config.maxTextWords)).text }
            ?.let { "“$it”" }
        val head = listOfNotNull(quote, credit?.takeIf(String::isNotBlank)?.let { "— ${it.trim()}" })
        val tail = "${invite.trim()} $link"
        return if (head.isEmpty()) tail else head.joinToString("\n") + "\n\n" + tail
    }
}
