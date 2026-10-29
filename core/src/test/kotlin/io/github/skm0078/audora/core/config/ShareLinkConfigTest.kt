// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/ShareLinkConfigTest.kt
// Purpose: The share link defaults to the prod site and refuses anything but a plain https URL.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareLinkConfigTest {
    @Test
    fun `the default is the prod site`() {
        assertEquals("https://skm0078.github.io/audora/", ShareLinkConfig().appLink)
    }

    @Test
    fun `pictures print no link until a short domain is set`() {
        assertEquals(null, ShareLinkConfig().cardLink)
        assertEquals("listenlock.app", ShareLinkConfig(cardLink = "listenlock.app").cardLink)
    }

    @Test
    fun `a plain http or spaced link is rejected`() {
        listOf(
            { ShareLinkConfig("http://example.org") },
            { ShareLinkConfig("https://example.org/a b") },
            { ShareLinkConfig(cardLink = " ") },
            { ShareLinkConfig(cardLink = "skm0078.github.io/audora/long") },
            { ShareCardConfig(maxWords = 60, maxTextWords = 10) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
