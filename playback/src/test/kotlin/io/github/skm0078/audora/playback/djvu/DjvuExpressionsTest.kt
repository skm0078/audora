// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/djvu/DjvuExpressionsTest.kt
// Purpose: DjVu s-expressions read as the native bridge and djvused write them: lists, numbers, symbols, escapes and
//          octal-escaped UTF-8; broken text is refused, not guessed at.

package io.github.skm0078.audora.playback.djvu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DjvuExpressionsTest {
    @Test
    fun `reads a zone with its kind, box and string`() {
        val word = DjvuExpressions.parse("""(word 60 947 108 967 "The")""") as DjvuExpression.Items
        assertEquals("word", word.head)
        assertEquals(
            listOf(
                DjvuExpression.Symbol("word"),
                DjvuExpression.Number(60),
                DjvuExpression.Number(947),
                DjvuExpression.Number(108),
                DjvuExpression.Number(967),
                DjvuExpression.Text("The"),
            ),
            word.items,
        )
    }

    @Test
    fun `decodes quotes, backslashes, C escapes and octal UTF-8`() {
        val text = DjvuExpressions.parse(""""say \"hi\" \\ a\tb \342\200\224 \340\244\256\340\244\250"""")
        assertEquals(DjvuExpression.Text("say \"hi\" \\ a\tb — मन"), text)
    }

    @Test
    fun `keeps UTF-8 that is written as it is`() {
        assertEquals(DjvuExpression.Text("जहाँ"), DjvuExpressions.parse("\"जहाँ\""))
    }

    @Test
    fun `reads expressions one after another, and nothing from blank text`() {
        assertEquals(2, DjvuExpressions.parseAll("(a)\n(b 1)\n").size)
        assertNull(DjvuExpressions.parse("  \n"))
        assertEquals(emptyList<DjvuExpression>(), DjvuExpressions.parseAll(""))
    }

    @Test
    fun `refuses an unclosed list or string and a stray parenthesis`() {
        assertThrows(DjvuExpressionException::class.java) { DjvuExpressions.parse("(page 0 0 1 1") }
        assertThrows(DjvuExpressionException::class.java) { DjvuExpressions.parse("(word \"open") }
        assertThrows(DjvuExpressionException::class.java) { DjvuExpressions.parse(")") }
        assertThrows(DjvuExpressionException::class.java) { DjvuExpressions.parse("\"ends with \\") }
    }
}
