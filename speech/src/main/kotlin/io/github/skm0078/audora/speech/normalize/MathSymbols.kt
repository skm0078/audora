// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/MathSymbols.kt
// Purpose: Reads maths and logic symbols in a display equation as words: ∀ "for all", → "implies" (RI-7b).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang

/**
 * Applied only to [TextRole.EQUATION] text, first, so the language's own rules then read the numbers; the equation's
 * structure (powers, roots, fractions, derivatives) is read by [MathStructure] before the symbols. Every sign has the
 * running-text word ([SymbolWords]) unless the tables here read it differently in an equation: "→" is "implies", not
 * "to"; "<" is "is less than" with no operand check; "/" is "over". Logic signs only Hindi and Bengali equations need
 * are here too. Word order stays the equation's ("x ≤ 3" → "x at most 3"), which is how maths is read aloud in English
 * and close enough in Hindi and Bengali, where an equation's operands are Latin letters read by the English voice.
 */
internal object MathSymbols {
    fun speak(text: String, lang: Lang): String {
        val table = TABLES.getValue(lang)
        val structured = MathStructure.speak(text, lang)
        val spoken = StringBuilder(structured.length * 2)
        structured.forEach { char ->
            val word = table[char] ?: SymbolWords.word(char, lang)
            if (word == null) spoken.append(char) else spoken.append(' ').append(word).append(' ')
        }
        return BINARY_MINUS.replace(spoken, " ${SymbolWords.word('−', lang)} ")
    }

    /** A hyphen between spaces is a minus sign in an equation ("a - b"); in a word it stays a hyphen. */
    private val BINARY_MINUS = Regex("""(?<=\s)[-−](?=\s)""")

    /** Only where an equation reads a sign unlike a sentence; the rest is [SymbolWords]'. */
    private val ENGLISH = mapOf(
        '~' to "not", '&' to "and", '→' to "implies", '⊃' to "implies", '↔' to "if and only if", '⊢' to "proves",
        '⊨' to "entails", '⊥' to "contradiction", '⊆' to "subset of or equal to", '≠' to "is not equal to",
        '≈' to "is approximately", '<' to "is less than", '>' to "is greater than", '·' to "times", '*' to "times",
        '/' to "over", '^' to "to the power",
    )

    private val HINDI = mapOf(
        '∀' to "सभी के लिए", '∃' to "कोई ऐसा है", '¬' to "नहीं", '∧' to "और", '&' to "और", '∨' to "या", '→' to "तो",
        '↔' to "तभी और केवल तभी", '<' to "से कम", '>' to "से अधिक", '*' to "गुणा", '/' to "बटा",
    )

    private val BENGALI = mapOf(
        '∀' to "সকলের জন্য", '∃' to "এমন আছে", '¬' to "নয়", '∧' to "এবং", '&' to "এবং", '∨' to "অথবা", '→' to "হলে",
        '↔' to "যদি এবং কেবল যদি", '<' to "এর চেয়ে কম", '>' to "এর চেয়ে বেশি", '*' to "গুণ", '/' to "বাই",
    )

    private val TABLES = mapOf(Lang.EN to ENGLISH, Lang.HI to HINDI, Lang.BN to BENGALI)
}
