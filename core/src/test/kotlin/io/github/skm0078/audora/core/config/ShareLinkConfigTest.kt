// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/ShareLinkConfigTest.kt
// Purpose: The share link defaults to the prod site and refuses anything but a plain https URL.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareLinkConfigTest {
    @Test
    fun `the default is the prod site's get page`() {
        assertEquals("https://listenlock.telosmindlabs.com/get/", ShareLinkConfig().appLink)
    }

    @Test
    fun `pictures print the site's bare host, or nothing when turned off`() {
        assertEquals("listenlock.telosmindlabs.com", ShareLinkConfig().cardLink)
        assertEquals(null, ShareLinkConfig(cardLink = null).cardLink)
    }

    @Test
    fun `a plain http or spaced link is rejected`() {
        listOf(
            { ShareLinkConfig("http://example.org") },
            { ShareLinkConfig("https://example.org/a b") },
            { ShareLinkConfig(cardLink = " ") },
            { ShareLinkConfig(cardLink = "skm0078.github.io/audora/long") },
            { ShareCardConfig(maxWords = 60, maxTextWords = 10) },
            { ShareCardConfig(maxWords = 60, maxPageWords = 10) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
