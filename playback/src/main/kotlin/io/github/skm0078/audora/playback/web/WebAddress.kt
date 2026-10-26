// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebAddress.kt
// Purpose: Turns what the listener typed or shared into one https address, or says why it cannot be fetched.

package io.github.skm0078.audora.playback.web

import java.net.URI
import java.net.URISyntaxException

/**
 * Only https is fetched: Android refuses plain http by default, and a page read aloud should not be changeable on the
 * way. An http address is tried as https (most sites serve both); any other scheme is refused. A bare
 * "example.org/page" gets https:// in front. The address must name a host with a dot, so a typo such as "hello"
 * fails here instead of as a network error.
 */
object WebAddress {
    /** The https address for [typed], or null when it is not a web address at all. */
    fun normalize(typed: String): String? {
        val trimmed = typed.trim()
        val withScheme = when {
            trimmed.isEmpty() || trimmed.any(Char::isWhitespace) -> null
            trimmed.startsWith(HTTPS, ignoreCase = true) -> trimmed
            trimmed.startsWith(HTTP, ignoreCase = true) -> HTTPS + trimmed.substring(HTTP.length)
            SCHEME.containsMatchIn(trimmed) -> null
            else -> HTTPS + trimmed
        }
        return withScheme?.let(::parse)?.takeIf(::isWebPage)?.toString()
    }

    private fun parse(address: String): URI? = try {
        URI(address)
    } catch (ignored: URISyntaxException) {
        null
    }

    /**
     * A host with a dot inside it, and no "user:password@" in front: that is how a link pretends to be another site,
     * and "mailto:x@y.org" also lands there.
     */
    private fun isWebPage(uri: URI): Boolean {
        val host = uri.host ?: return false
        return '.' in host && !host.startsWith('.') && !host.endsWith('.') && uri.rawUserInfo == null
    }

    /** The first web address in shared text (a link with a title or a sentence around it), normalized. */
    fun firstIn(text: String): String? = LINK.find(text)?.value?.trimEnd { it in TRAILING }?.let(::normalize)

    /** Where [address] lives, for messages and logs (never the whole address: it can carry private tokens). */
    fun hostOf(address: String): String = parse(address)?.host.orEmpty().removePrefix(WWW)

    private const val HTTPS = "https://"
    private const val HTTP = "http://"
    private const val WWW = "www."
    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
    private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** Sentence punctuation stuck to a link's end in a chat message is not part of it. */
    private const val TRAILING = ".,;:!?)]\"'"
}
