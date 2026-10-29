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
    fun `a picture prints the link without its scheme or trailing slash`() {
        assertEquals("skm0078.github.io/audora", ShareLinkConfig().shownHost)
        assertEquals("listenlock.app", ShareLinkConfig("https://listenlock.app").shownHost)
    }

    @Test
    fun `a plain http or spaced link is rejected`() {
        listOf(
            { ShareLinkConfig("http://example.org") },
            { ShareLinkConfig("https://example.org/a b") },
            { ShareCardConfig(maxWords = 60, maxTextWords = 10) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
