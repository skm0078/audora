// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ShareLinkConfig.kt
// Purpose: The one link every share carries (ADR 2026-10-29-share-credit-and-link), kept in one place so the domain
//          and the Play listing can replace it without touching any share.

package io.github.skm0078.audora.core.config

/**
 * @property appLink the page a shared picture, video, clip or text points to. It must be served from `prod` (the site
 *   is GitHub Pages from prod's /docs), never `dev`, and carry no tracking: ListenLock counts nothing. Never a link to
 *   the book itself.
 */
data class ShareLinkConfig(val appLink: String = APP_SITE) {
    init {
        require(appLink.startsWith("https://") && appLink.none(Char::isWhitespace)) {
            "appLink must be an https URL without spaces, was $appLink"
        }
    }

    /** The link as printed small on a picture: no scheme, no trailing slash ("skm0078.github.io/audora"). */
    val shownHost: String get() = appLink.removePrefix("https://").trimEnd('/')

    companion object {
        /** The site's home, from prod; swap for the domain or the Play listing once either is live. */
        const val APP_SITE = "https://skm0078.github.io/audora/"
    }
}
