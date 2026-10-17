// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReadingLookTest.kt
// Purpose: Table-driven checks of "bold word starts": which letters are bolded, and that Indic words stay whole.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingLookTest {
    @Test
    fun `the first half of each English word is bolded, rounded up`() {
        val cases = mapOf(
            "a" to listOf("a"),
            "the map" to listOf("t", "m"),
            "true places" to listOf("tr", "pla"),
            "never," to listOf("nev"),
            "It is not down in any map; true places never are." to
                listOf("I", "i", "n", "do", "i", "a", "m", "tr", "pla", "nev", "a"),
            "" to emptyList(),
            "12 34!" to emptyList(),
        )
        cases.forEach { (text, bolded) ->
            assertEquals("for '$text'", bolded, boldWordStartRanges(text).map { text.substring(it.first, it.last + 1) })
        }
    }

    @Test
    fun `Hindi and Bengali words are never split`() {
        val cases = listOf("सभी मनुष्यों को", "কৃষি নির্দেশিকা", "प्रत्येक")
        cases.forEach { text -> assertEquals("for '$text'", emptyList<IntRange>(), boldWordStartRanges(text)) }
    }

    @Test
    fun `mixed lines bold only the English words`() {
        val text = "Audora पढ़ता है PDFs"
        assertEquals(listOf("Aud", "PD"), boldWordStartRanges(text).map { text.substring(it.first, it.last + 1) })
    }
}
