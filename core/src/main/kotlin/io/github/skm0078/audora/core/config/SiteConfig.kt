// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SiteConfig.kt
// Purpose: The public site's address, kept in one place so shares, Settings and the Play listing always agree
//          (ADR 2026-10-30-company-domain).

package io.github.skm0078.audora.core.config

/**
 * ListenLock's section of the company site. The company, TelosMind Labs, owns `telosmindlabs.com`; every app gets
 * its own subdomain, and this app's is [HOST]. GitHub Pages serves it from `prod`'s `/docs` (`docs/CNAME` names the
 * same host), so a page only goes live through a dev → prod release.
 */
object SiteConfig {
    /** Short enough to print on a share card under the ListenLock mark, and carries both names. */
    const val HOST = "listenlock.telosmindlabs.com"

    const val ROOT = "https://$HOST/"

    /** docs/get/index.html: the page every share links to; it forwards Android phones to Play once listed. */
    const val GET_PAGE = "${ROOT}get/"

    /** docs/privacy.html: the same page the Play listing links to. */
    const val PRIVACY_PAGE = "${ROOT}privacy.html"

    /** Where readers and testers write; shown on the site and in the privacy policy. */
    const val SUPPORT_EMAIL = "support@telosmindlabs.com"
}
