// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/SpeakableText.kt
// Purpose: The normalizers' last step: leaves out what no voice can say (emoji, dingbats, broken-font glyphs).

package io.github.skm0078.audora.speech.normalize

/**
 * Runs after every rule, so whatever a rule could read is already words. What is left that is not a letter, a digit,
 * a combining mark on a letter, a space or punctuation a voice phrases with (stops, commas, quotes, brackets, dashes,
 * the danda) is left out: a voice says such a character as a noise, a Unicode name or nothing, and a character-based
 * Hindi or Bengali voice may garble the word next to it. That covers emoji (with their variation selectors and
 * joiners), stars, a font's private-use glyphs, the replacement character "�", footnote daggers and superscripts, and
 * stray "*", "#", "@", "^" or "_". Bullets, pipes and middle dots between words become a comma, so "Red • Green"
 * keeps its pause. ZWJ and ZWNJ stay after a letter: they shape Devanagari and Bengali clusters.
 *
 * Then the pauses it and [SymbolWords] add are tidied: no space before a comma or a full stop, no comma twice, after a
 * colon or before a full stop, none at the start ("Name: _____" is "Name:"). A comma ending an equation's group stays.
 * The result may be blank ("★★★"): the caller then says nothing.
 */
internal object SpeakableText {
    fun clean(text: String): String = tidy(if (text.all(::isPlainAscii)) text else speakable(text))

    private fun speakable(text: String): String {
        val out = StringBuilder(text.length)
        var previous = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when (kindOf(cp, previous)) {
                Kind.KEEP -> {
                    out.appendCodePoint(cp)
                    previous = cp
                }

                Kind.PAUSE -> {
                    out.append(", ")
                    previous = SPACE
                }

                Kind.DROP -> {
                    out.append(' ')
                    previous = SPACE
                }
            }
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private enum class Kind { KEEP, PAUSE, DROP }

    private fun kindOf(cp: Int, previous: Int): Kind {
        val type = Character.getType(cp)
        return when {
            Character.isLetter(cp) || type == DIGIT || Character.isWhitespace(cp) -> Kind.KEEP
            type in MARKS || cp == ZWJ || cp == ZWNJ -> if (joinsLetter(cp, previous)) Kind.KEEP else Kind.DROP
            type in PHRASING_PUNCTUATION || cp in PHRASING_MARKS -> Kind.KEEP
            cp in SEPARATORS -> Kind.PAUSE
            else -> Kind.DROP
        }
    }

    /** A combining mark or joiner shapes the letter before it; after a dropped emoji it is left out with it. */
    private fun joinsLetter(cp: Int, previous: Int): Boolean =
        cp !in VARIATION_SELECTORS && cp != KEYCAP && onLetter(previous)

    private fun onLetter(cp: Int): Boolean = Character.isLetter(cp) || Character.getType(cp) in MARKS ||
        cp == ZWJ || cp == ZWNJ

    private fun isPlainAscii(c: Char): Boolean = c.code < ASCII_END && (c.isLetterOrDigit() || c in PLAIN_ASCII)

    private fun tidy(text: String): String {
        var out = SPACES.replace(text, " ")
        out = SPACE_BEFORE_COMMA.replace(out, ",")
        out = SPACE_BEFORE_STOP.replace(out, "$1")
        out = REPEATED_PAUSE.replace(out, "$1")
        out = PAUSE_BEFORE_STOP.replace(out, "$1")
        return LEADING_PAUSES.replace(out, "").trimEnd()
    }

    private const val SPACE = ' '.code
    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C
    private const val KEYCAP = 0x20E3
    private const val ASCII_END = 0x80
    private const val DIGIT = Character.DECIMAL_DIGIT_NUMBER.toInt()

    private const val FIRST_VARIATION_SELECTOR = 0xFE00
    private const val LAST_VARIATION_SELECTOR = 0xFE0F
    private val VARIATION_SELECTORS = FIRST_VARIATION_SELECTOR..LAST_VARIATION_SELECTOR
    private val MARKS = setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
    )

    /** Dashes, opening and closing brackets and quotes: kept whatever the script. */
    private val PHRASING_PUNCTUATION = setOf(
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    )

    /** "Other" punctuation a voice phrases with; the rest of that class ("*", "#", "†", "§") is left out. */
    private val PHRASING_MARKS = ".,;:!?'\"…/।॥¿¡".map(Char::code).toSet()

    /** ASCII that needs no look: letters, digits and the marks above, plus spaces. */
    private const val PLAIN_ASCII = " .,;:!?'\"/()[]{}-"

    /** Marks that separate items in a line: read as the pause they stand for. */
    private val SEPARATORS = "•◦‣⁃∙·‧▪▫●○■□►▸▹▶➢➤❖◆◇|¦".map(Char::code).toSet()

    private val SPACES = Regex("""[ \t]{2,}""")
    private val SPACE_BEFORE_COMMA = Regex("""[ \t]+,""")
    private val SPACE_BEFORE_STOP = Regex("""[ \t]+([.!?])(?=\s|$)""")
    private val REPEATED_PAUSE = Regex("""([,;:])(?:[ \t]*,)+""")
    private val PAUSE_BEFORE_STOP = Regex(""",[ \t]*([.!?।॥])""")
    private val LEADING_PAUSES = Regex("""^[\s,;:]+""")
}
