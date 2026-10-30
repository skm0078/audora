// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/SiteConfigTest.kt
// Purpose: Every public link the app hands out is on ListenLock's company subdomain, over https, and the card's
//          short link is that same host.

package io.github.skm0078.audora.core.config

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteConfigTest {
    @Test
    fun `every page is on the company subdomain over https`() {
        listOf(SiteConfig.ROOT, SiteConfig.GET_PAGE, SiteConfig.PRIVACY_PAGE).forEach { page ->
            val uri = URI(page)
            assertEquals(page, "https", uri.scheme)
            assertEquals(page, "listenlock.telosmindlabs.com", uri.host)
        }
    }

    @Test
    fun `shares link to the get page and print the bare host`() {
        assertEquals(SiteConfig.GET_PAGE, ShareLinkConfig().appLink)
        assertEquals(SiteConfig.HOST, ShareLinkConfig().cardLink)
    }

    @Test
    fun `the support address is on the company domain`() {
        assertEquals("telosmindlabs.com", SiteConfig.SUPPORT_EMAIL.substringAfter('@'))
    }
}
