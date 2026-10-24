// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/ClosingMarksTest.kt
// Purpose: A sentence never ends in a piece holding only a closing quote or bracket, which a voice babbles.

package io.github.skm0078.audora.speech.sherpa

import org.junit.Assert.assertEquals
import org.junit.Test

class ClosingMarksTest {
    @Test
    fun `closing marks after the last end mark move inside it`() {
        val cases = listOf(
            "He said, “I am going home.”" to "He said, “I am going home”.",
            "He said, \"I am going home.\"" to "He said, \"I am going home\".",
            "“Stop!”" to "“Stop”!",
            "“Really?!”" to "“Really”?!",
            "It was ‘fine.’" to "It was ‘fine’.",
            "He wrote (see below.)" to "He wrote (see below).",
            "The word «free.»" to "The word «free».",
            "It ended…”" to "It ended”…",
            "(“Go.”) " to "(“Go”).",
            "“Yes.” ”" to "“Yes””.",
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, ClosingMarks.inside(text)) }
    }

    @Test
    fun `text that already ends on its mark, or has marks only inside, is unchanged`() {
        listOf(
            "He said hello.",
            "He said, “I am going home.” Then he left.",
            "“Where?” he asked.",
            "No end mark”",
            "It’s John’s book.",
            "The students’ books.",
            "",
        ).forEach { assertEquals(it, ClosingMarks.inside(it)) }
    }
}
