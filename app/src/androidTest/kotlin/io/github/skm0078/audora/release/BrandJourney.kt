// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/BrandJourney.kt
// Purpose: Release journey step 02: the launcher shows the public name, ListenLock, and never the code name Audora;
//          every link the app hands out (shares, the card's short link, Settings → Privacy policy) is on ListenLock's
//          company subdomain, never the old github.io address or a Gmail address (ADR 2026-10-30-company-domain).

package io.github.skm0078.audora.release

import io.github.skm0078.audora.core.config.ShareLinkConfig
import io.github.skm0078.audora.core.config.SiteConfig
import io.github.skm0078.audora.release.JourneyDevice.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object BrandJourney {
    fun nameAndLinksAreListenLocks() {
        theLauncherSaysListenLock()
        linksUseTheCompanySubdomain()
    }

    private fun theLauncherSaysListenLock() {
        val label = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        assertTrue("Launcher label was \"$label\"", label.startsWith("ListenLock"))
        assertFalse("Launcher label was \"$label\"", label.contains("Audora", ignoreCase = true))
    }

    private fun linksUseTheCompanySubdomain() {
        val share = ShareLinkConfig()
        listOf(share.appLink, SiteConfig.PRIVACY_PAGE).forEach { link ->
            assertTrue("Link was \"$link\"", link.startsWith("https://listenlock.telosmindlabs.com/"))
            assertFalse("Link was \"$link\"", link.contains("github.io"))
        }
        assertEquals("listenlock.telosmindlabs.com", share.cardLink)
        assertFalse(SiteConfig.SUPPORT_EMAIL.contains("gmail", ignoreCase = true))
    }
}
