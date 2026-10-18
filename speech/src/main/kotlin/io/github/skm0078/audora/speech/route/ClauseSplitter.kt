// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/ClauseSplitter.kt
// Purpose: Cuts a routed sentence at strong punctuation so each mark gets a real pause (RI-6, ADR-035).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.Lang

/**
 * One piece of a sentence between strong marks, read by [lang]'s voice.
 *
 * @property pauseBefore the mark that separates it from the piece before; null for the first piece and where only
 *   the voice changes.
 * @property aside inside brackets: read a little quieter.
 */
data class Clause(val text: String, val lang: Lang, val pauseBefore: ClauseMark?, val aside: Boolean)

/**
 * Splits after script routing, so routing decisions made on the whole sentence (a short English run staying with
 * the Hindi voice) hold. Only marks a reader stops at cut: em dash, a spaced dash or hyphen, a colon or semicolon
 * followed by a space, brackets, double quotes, an ellipsis, and a danda inside the text. Commas never cut, so the
 * voice keeps its sentence intonation. A dash between digits ("10 – 20") is a range for the normalizer, a colon
 * without a space ("10:30", "https:") is not a mark, and a closing bracket with no opening one ("a) first") stays.
 * Brackets around a short label ("(i)", "(a)", "(2.1)", "word(s)") are not an aside: they stay for the normalizer,
 * which reads "(iv)" as "four".
 * The marks themselves are dropped: the pause replaces them. "?" and "!" are never touched.
 *
 * Pure and deterministic; pieces are trimmed and hold at least one letter or digit. A symbol standing alone between
 * two marks ("(a + b) = (c + d)", "¬(P)") is never dropped: it is read with the next piece, so only the pause is
 * placed, not the text lost.
 */
object ClauseSplitter {
    /** Strongest first: where two marks meet ("—(") the pause is the stronger one's. */
    private val STRENGTH = listOf(
        ClauseMark.ELLIPSIS,
        ClauseMark.DANDA,
        ClauseMark.COLON,
        ClauseMark.SEMICOLON,
        ClauseMark.DASH,
        ClauseMark.BRACKET,
        ClauseMark.QUOTE,
    )
    private const val ELLIPSIS_DOTS = 3

    /** The longest bracketed label kept whole: "(xviii)", "(12a)", "(2.14)". */
    private const val LABEL_MAX_CHARS = 5
    private val NUMBER_LABEL = Regex("""\p{Nd}+(?:\.\p{Nd}+)*\p{L}?""")
    private val ROMAN_LABEL = Regex("""x{0,3}(?:ix|iv|v?i{0,3})""", RegexOption.IGNORE_CASE)
    private val OPENING = setOf('(', '[')
    private val CLOSING = setOf(')', ']')
    private val DANDAS = setOf('।', '॥')

    /** A time word ending the text before a colon ("समय", "সময়" in both spellings of য়). */
    private val TIME_LABEL = Regex("""(?<![\p{L}\p{M}])(?:समय|সম(?:\u09DF|\u09AF\u09BC))\s*$""")

    /** Marks that are one character wherever they stand. */
    private val SINGLE = mapOf(';' to ClauseMark.SEMICOLON) +
        listOf('"', '“', '”', '«', '»', '„').associateWith { ClauseMark.QUOTE }

    fun split(segments: List<ScriptSegment>): List<Clause> {
        val state = State()
        for (segment in segments) {
            state.lang = segment.lang
            scan(segment.text, state)
            state.flush() // a voice change alone adds no pause; a mark at the segment's end carries over
        }
        state.finish()
        return state.clauses
    }

    private class State {
        val clauses = ArrayList<Clause>()
        val buffer = StringBuilder()
        var lang = Lang.EN
        var depth = 0
        var pending: ClauseMark? = null

        fun mark(mark: ClauseMark) {
            flush()
            val current = pending
            pending = if (current == null || STRENGTH.indexOf(mark) < STRENGTH.indexOf(current)) mark else current
        }

        /** A piece with no letter or digit but a maths or other symbol ("=", "¬"): read with the next piece. */
        var carried = ""

        fun flush() {
            val text = buffer.trim().toString()
            buffer.setLength(0)
            if (text.none(Char::isLetterOrDigit)) {
                if (text.any(::isSymbol)) carried = "$carried $text".trim()
                return
            }
            val spoken = if (carried.isEmpty()) text else "$carried $text"
            carried = ""
            clauses.add(Clause(spoken, lang, pending.takeIf { clauses.isNotEmpty() }, aside = depth > 0))
            pending = null
        }

        /** Unicode maths and other symbols: "=", "+", "¬", "→", "§". */
        private fun isSymbol(c: Char): Boolean =
            Character.getType(c).let { it == Character.MATH_SYMBOL.toInt() || it == Character.OTHER_SYMBOL.toInt() }

        /** A symbol left after the last piece goes on the end of it, never dropped. */
        fun finish() {
            if (carried.isEmpty()) return
            val last = clauses.removeLastOrNull()
            clauses.add(last?.copy(text = "${last.text} $carried") ?: Clause(carried, lang, null, aside = false))
            carried = ""
        }
    }

    private fun scan(text: String, state: State) {
        var i = 0
        while (i < text.length) {
            val consumed = markAt(text, i, state)
            if (consumed == 0) {
                state.buffer.append(text[i])
                i++
            } else {
                i += consumed
            }
        }
    }

    /** Characters taken by a mark at [i] (0: none), after recording it in [state]. */
    private fun markAt(text: String, i: Int, state: State): Int =
        ellipsisAt(text, i, state) ?: bracketAt(text, i, state) ?: punctuationAt(text, i, state) ?: 0

    private fun ellipsisAt(text: String, i: Int, state: State): Int? {
        val dots = if (text[i] == '.') text.drop(i).takeWhile { it == '.' }.length else 0
        if (text[i] != '…' && dots < ELLIPSIS_DOTS) return null
        state.mark(ClauseMark.ELLIPSIS)
        return maxOf(1, dots)
    }

    private fun bracketAt(text: String, i: Int, state: State): Int? = when {
        // A label is kept whole, brackets and all, so its ")" never closes an outer bracket ("(see Eq. (3) below)").
        text[i] in OPENING && opensLabel(text, i) -> {
            // The label's content and both brackets.
            val label = text.substring(i, i + text.drop(i + 1).takeWhile { it !in CLOSING }.length + 2)
            state.buffer.append(label)
            label.length
        }

        text[i] in OPENING -> {
            state.mark(ClauseMark.BRACKET)
            state.depth++
            1
        }

        text[i] in CLOSING && state.depth > 0 -> {
            state.mark(ClauseMark.BRACKET)
            state.depth--
            1
        }

        else -> null
    }

    /**
     * A label, not an aside: a number ("(2)", "(2.14)", "(4a)"), one letter ("(a)", "(क)"), a roman numeral up to
     * thirty-nine ("(iv)", "(XII)"), or a short ending glued to a word ("word(s)").
     */

    private fun opensLabel(text: String, i: Int): Boolean {
        val content = text.drop(i + 1).takeWhile { it !in CLOSING && it !in OPENING }
        if (text.getOrNull(i + 1 + content.length) !in CLOSING || content.length !in 1..LABEL_MAX_CHARS) return false
        val glued = text.getOrNull(i - 1)?.isLetter() == true && content.all(Char::isLetter)
        return glued || NUMBER_LABEL.matches(content) || content.singleOrNull()?.isLetter() == true ||
            ROMAN_LABEL.matches(content)
    }

    private fun punctuationAt(text: String, i: Int, state: State): Int? {
        val c = text[i]
        return when {
            c in SINGLE -> markOne(state, SINGLE.getValue(c))

            c in DANDAS && text.drop(i + 1).any(Char::isLetterOrDigit) -> markOne(state, ClauseMark.DANDA)

            c == ':' && colonCuts(text, i) -> markOne(state, ClauseMark.COLON)

            isDash(text, i) -> {
                state.mark(ClauseMark.DASH)
                // "--" and "---" are one dash.
                maxOf(1, text.drop(i).takeWhile { it == '-' }.length)
            }

            else -> null
        }
    }

    private fun markOne(state: State, mark: ClauseMark): Int {
        state.mark(mark)
        return 1
    }

    private fun isDash(text: String, i: Int): Boolean {
        val c = text[i]
        val before = text.getOrNull(i - 1)
        val after = text.getOrNull(i + maxOf(1, text.drop(i).takeWhile { it == '-' }.length))
        // The nearest non-space characters on both sides are digits: "10 – 20" is a range, not a pause.
        val betweenDigits = text.take(i).trimEnd().lastOrNull()?.isDigit() == true &&
            text.drop(i + 1).trimStart('-', ' ').firstOrNull()?.isDigit() == true
        return when {
            betweenDigits -> false
            c == '—' || c == '―' || text.startsWith("--", i) -> true
            c == '–' || c == '-' -> before?.isWhitespace() == true && after?.isWhitespace() == true
            else -> false
        }
    }

    /**
     * A colon cuts when a space or the end follows it, except after "समय"/"সময়" before a time: "समय: 10:30" stays one
     * clause, since the bare "10:30" would lose the word that makes it a clock time for the Hindi and Bengali
     * normalizer, and be read as two numbers.
     */
    private fun colonCuts(text: String, i: Int): Boolean {
        val spaced = i + 1 >= text.length || text[i + 1].isWhitespace()
        val labelsATime = TIME_LABEL.containsMatchIn(text.take(i)) &&
            text.drop(i + 1).trimStart().firstOrNull()?.isDigit() == true
        return spaced && !labelsATime
    }
}
