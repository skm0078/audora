// File: core/src/test/kotlin/io/github/skm0078/audora/core/text/TextCutsTest.kt
// Purpose: Cuts keep conjuncts, joined forms and surrogate pairs whole, but may follow a word-ending halant.

package io.github.skm0078.audora.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCutsTest {
    private data class Case(val name: String, val text: String, val max: Int, val expected: String)

    @Test
    fun `cuts fall only between whole characters`() {
        listOf(
            Case("a conjunct stays whole", "अक्ष", 3, "अ"),
            Case("a word-ending halant may end the cut", "जगत् है", 4, "जगत्"),
            Case("a Bengali halant before a letter does not", "বক্তা", 3, "ব"),
            Case("a joined form stays whole", "\u09B0\u200D\u09CD\u09AF\u09BE\u09AC", 2, ""),
            Case("a surrogate pair stays whole", "a😀b", 2, "a"),
            Case("short text is kept", "क्ष", 10, "क्ष"),
        ).forEach { case -> assertEquals(case.name, case.expected, TextCuts.take(case.text, case.max)) }
    }
}
