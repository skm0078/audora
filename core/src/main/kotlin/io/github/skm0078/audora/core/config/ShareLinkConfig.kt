// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ShareLinkConfig.kt
// Purpose: The one link every share carries (ADR 2026-10-29-share-credit-and-link), kept in one place so the domain
//          and the Play listing can replace it without touching any share.

package io.github.skm0078.audora.core.config

/**
 * @property appLink the page a shared picture, video, clip or text points to. It must be served from `prod` (the site
 *   is GitHub Pages from prod's /docs), never `dev`, and carry no tracking: ListenLock counts nothing. Never a link to
 *   the book itself.
 * @property cardLink what a picture prints small under the ListenLock mark, or null for the logo and name alone (the
 *   maintainer's pick: a long link clutters the card, so only a short domain such as "listenlock.app" is printed, once
 *   it is live; the message beside the picture always carries [appLink]).
 */
data class ShareLinkConfig(val appLink: String = APP_SITE, val cardLink: String? = CARD_LINK) {
    init {
        require(appLink.startsWith("https://") && appLink.none(Char::isWhitespace)) {
            "appLink must be an https URL without spaces, was $appLink"
        }
        require(cardLink == null || (cardLink.isNotBlank() && cardLink.length <= MAX_CARD_LINK)) {
            "cardLink must be a short domain of at most $MAX_CARD_LINK characters, was $cardLink"
        }
    }

    companion object {
        /** The site's home, from prod; swap for the domain or the Play listing once either is live. */
        const val APP_SITE = "https://skm0078.github.io/audora/"

        /** Nothing printed until the short domain is live; then its bare name, such as "listenlock.app". */
        val CARD_LINK: String? = null

        // Longer than this no longer reads at the mark's small size beside the name.
        private const val MAX_CARD_LINK = 24
    }
}
