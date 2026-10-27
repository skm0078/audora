// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/djvu/DjvuExpressions.kt
// Purpose: Reads the s-expressions DjVuLibre gives for a page's hidden text, the outline and the annotations
//          (ADR 2026-10-27-djvu-books): as the native bridge writes them, and as `djvused -u` prints them.

package io.github.skm0078.audora.playback.djvu

import java.io.ByteArrayOutputStream

/** One DjVu s-expression: a list, a quoted string, a number or a bare symbol (`page`, `word`, `bookmarks`). */
sealed interface DjvuExpression {
    data class Items(val items: List<DjvuExpression>) : DjvuExpression

    data class Text(val value: String) : DjvuExpression

    data class Number(val value: Int) : DjvuExpression

    data class Symbol(val name: String) : DjvuExpression
}

/** The list's first item when it is a symbol: the kind of a text zone (`line`) or an annotation (`metadata`). */
val DjvuExpression.Items.head: String? get() = (items.firstOrNull() as? DjvuExpression.Symbol)?.name

/** Thrown for text that is not one whole s-expression; the caller reports the page or book as damaged. */
class DjvuExpressionException(message: String) : Exception(message)

object DjvuExpressions {
    private const val OCTAL_RADIX = 8
    private const val OCTAL_DIGITS = 3
    private val OCTAL = '0'..'7'
    private val C_ESCAPES = mapOf('n' to '\n', 't' to '\t', 'r' to '\r', 'f' to '\u000C', 'b' to '\b', 'v' to '\u000B')

    /**
     * The expressions in [source], in order; empty for blank text. Strings may hold `\"`, `\\`, C escapes and
     * octal bytes (`\342\200\224`, how djvused prints UTF-8 without `-u`), all decoded as UTF-8.
     */
    fun parseAll(source: String): List<DjvuExpression> {
        val reader = Reader(source)
        val all = ArrayList<DjvuExpression>()
        while (reader.skipSpace()) all += reader.expression()
        return all
    }

    /** The one expression in [source], or null for blank text. */
    fun parse(source: String): DjvuExpression? = parseAll(source).firstOrNull()

    private class Reader(private val text: String) {
        private var at = 0

        /** Skips spaces and line breaks; false at the end. */
        fun skipSpace(): Boolean {
            while (at < text.length && text[at].isWhitespace()) at++
            return at < text.length
        }

        fun expression(): DjvuExpression {
            if (!skipSpace()) throw DjvuExpressionException("expression expected at the end")
            return when (text[at]) {
                '(' -> items()
                '"' -> DjvuExpression.Text(string())
                ')' -> throw DjvuExpressionException("unexpected ')' at $at")
                else -> atom()
            }
        }

        private fun items(): DjvuExpression.Items {
            at++
            val items = ArrayList<DjvuExpression>()
            while (true) {
                if (!skipSpace()) throw DjvuExpressionException("unclosed list")
                if (text[at] == ')') {
                    at++
                    return DjvuExpression.Items(items)
                }
                items += expression()
            }
        }

        private fun atom(): DjvuExpression {
            val start = at
            while (at < text.length && isAtomChar(text[at])) at++
            val word = text.substring(start, at)
            return word.toIntOrNull()?.let { DjvuExpression.Number(it) } ?: DjvuExpression.Symbol(word)
        }

        private fun isAtomChar(ch: Char): Boolean = !ch.isWhitespace() && ch != '(' && ch != ')'

        /** A quoted string; escapes collect as bytes, so octal-escaped UTF-8 comes out whole. */
        private fun string(): String {
            at++
            val bytes = ByteArrayOutputStream()
            while (true) {
                if (at >= text.length) throw DjvuExpressionException("unclosed string")
                val ch = text[at++]
                when (ch) {
                    '"' -> return bytes.toString(Charsets.UTF_8.name())
                    '\\' -> escape(bytes)
                    else -> bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
                }
            }
        }

        private fun escape(bytes: ByteArrayOutputStream) {
            if (at >= text.length) throw DjvuExpressionException("unclosed string")
            val ch = text[at++]
            val simple = C_ESCAPES[ch]
            when {
                simple != null -> bytes.write(simple.code)

                ch in OCTAL -> bytes.write(octal(ch - '0'))

                // `\"`, `\\` and any other escaped character stand for themselves.
                else -> bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
            }
        }

        /** An octal byte of up to three digits, the first already read as [first]. */
        private fun octal(first: Int): Int {
            var value = first
            var digits = 1
            while (digits < OCTAL_DIGITS && at < text.length && text[at] in OCTAL) {
                value = value * OCTAL_RADIX + (text[at++] - '0')
                digits++
            }
            return value
        }
    }
}
