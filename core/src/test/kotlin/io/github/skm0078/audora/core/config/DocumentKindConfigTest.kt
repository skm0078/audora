// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/DocumentKindConfigTest.kt
// Purpose: DocumentKindConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentKindConfigTest {
    @Test
    fun `defaults are valid`() {
        DocumentKindConfig()
    }

    @Test
    fun `out-of-range values are rejected`() {
        listOf(
            { DocumentKindConfig(samplePages = 0) },
            { DocumentKindConfig(minWords = -1) },
            { DocumentKindConfig(formTableShare = 1.5f) },
            { DocumentKindConfig(formDigitShare = -0.1f) },
            { DocumentKindConfig(paperCitationRate = -1f) },
            { DocumentKindConfig(gazetteTermRate = -1f) },
            { DocumentKindConfig(novelMaxStructureRate = -1f) },
            { DocumentKindConfig(maxPaperPages = 0) },
            { DocumentKindConfig(minTextbookPages = 0) },
            { DocumentKindConfig(novelSentenceWords = 0f) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
