// File: core/src/test/kotlin/io/github/skm0078/audora/core/stats/WordCountTest.kt
// Purpose: Words are counted the same way in English, Hindi and Bengali, and punctuation alone is not a word.

package io.github.skm0078.audora.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class WordCountTest {
    @Test
    fun `words in each language`() {
        val cases = listOf(
            "" to 0,
            "   " to 0,
            "Hello, world - it's 2026!" to 4,
            "मैं  घर   जा रहा हूँ।" to 5,
            "আমি বাড়ি যাচ্ছি ।" to 3,
            "“ — ”" to 0,
        )
        cases.forEach { (text, words) -> assertEquals("\"$text\"", words, WordCount.of(text)) }
    }
}
