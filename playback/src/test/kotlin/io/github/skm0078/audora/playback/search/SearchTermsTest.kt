// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/search/SearchTermsTest.kt
// Purpose: Index and query terms agree for English, Hindi and Bengali, whatever the punctuation and joiners.

package io.github.skm0078.audora.playback.search

import io.github.skm0078.audora.core.config.SearchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTermsTest {
    @Test
    fun `splits on punctuation and keeps marks`() {
        val cases = listOf(
            "Where the mind is without fear," to listOf("where", "the", "mind", "is", "without", "fear"),
            "It's 1,200 km—far." to listOf("it", "s", "1", "200", "km", "far"),
            "मन भय से मुक्त हो।" to listOf("मन", "भय", "से", "मुक्त", "हो"),
            "चित्त जेथा भयशून्य॥ उच्च" to listOf("चित्त", "जेथा", "भयशून्य", "उच्च"),
            "চিত্ত যেথা ভয়শূন্য, উচ্চ যেথা শির।" to listOf("চিত্ত", "যেথা", "ভয়শূন্য", "উচ্চ", "যেথা", "শির"),
            "  " to emptyList(),
        )
        for ((text, terms) in cases) assertEquals(text, terms, SearchTerms.of(text))
    }

    @Test
    fun `a joiner or a decomposed letter still matches`() {
        // র্‌ with ZWNJ vs without; য় precomposed (U+09DF) vs য + nukta.
        assertEquals(SearchTerms.of("কর্ম"), SearchTerms.of("কর্‌ম"))
        assertEquals(SearchTerms.of("ভয়"), SearchTerms.of("ভয়"))
        assertEquals(SearchTerms.of("Fear"), SearchTerms.of("FEAR"))
    }

    @Test
    fun `the query needs every word and treats the last as a prefix`() {
        assertEquals("mind without*", SearchTerms.match("Mind, without"))
        assertEquals("भय*", SearchTerms.match("भय"))
        assertEquals("and or*", SearchTerms.match("AND OR"))
        assertNull(SearchTerms.match("a"))
        assertNull(SearchTerms.match(" ,. "))
        assertEquals("a b*", SearchTerms.match("a b c", SearchConfig(maxQueryTerms = 2)))
    }
}
