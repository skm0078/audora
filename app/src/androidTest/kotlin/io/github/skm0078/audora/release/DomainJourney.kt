// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/DomainJourney.kt
// Purpose: Release journey step: every link the installed app hands out (shares, the card's short link, Settings →
//          Privacy policy) is on ListenLock's company subdomain, never the old github.io address or a Gmail address.

package io.github.skm0078.audora.release

import io.github.skm0078.audora.core.config.ShareLinkConfig
import io.github.skm0078.audora.core.config.SiteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object DomainJourney {
    fun linksUseTheCompanySubdomain() {
        val share = ShareLinkConfig()
        val links = listOf(share.appLink, SiteConfig.PRIVACY_PAGE)
        links.forEach { link ->
            assertTrue("Link was \"$link\"", link.startsWith("https://listenlock.telosmindlabs.com/"))
            assertFalse("Link was \"$link\"", link.contains("github.io"))
        }
        assertEquals("listenlock.telosmindlabs.com", share.cardLink)
        assertFalse(SiteConfig.SUPPORT_EMAIL.contains("gmail", ignoreCase = true))
    }
}
