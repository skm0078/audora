// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ShareLinkConfig.kt
// Purpose: The one link every share carries (ADR 2026-10-29-share-credit-and-link), kept in one place so the domain
//          and the Play listing can replace it without touching any share.

package io.github.skm0078.audora.core.config

/**
 * @property appLink the page a shared picture, video, clip or text points to. It must be served from `prod` (the site
 *   is GitHub Pages from prod's /docs), never `dev`, and carry no tracking: ListenLock counts nothing. Never a link to
 *   the book itself.
 * @property cardLink what a picture prints small under the ListenLock mark, or null for the logo and name alone (the
 *   maintainer's pick: a long link clutters the card, so only the bare site host is printed, never a path; the
 *   message beside the picture always carries [appLink]).
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
        /**
         * The site's "get" page, from prod (docs/get/index.html): Android phones go on to the Play listing once it is
         * public, everyone else to the site, so links already shared follow the launch. Only the domain changes it.
         */
        const val APP_SITE = SiteConfig.GET_PAGE

        /** The site's bare host: on a card it names both the app and the company (ADR 2026-10-30-company-domain). */
        val CARD_LINK: String? = SiteConfig.HOST

        // Longer than this no longer reads at the mark's small size beside the name. 28 fits the company subdomain
        // ("listenlock.telosmindlabs.com"); a card is 360 card-dp wide and the line needs about 140 of them.
        private const val MAX_CARD_LINK = 28
    }
}
