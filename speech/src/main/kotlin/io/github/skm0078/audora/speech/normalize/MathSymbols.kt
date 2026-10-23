// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/MathSymbols.kt
// Purpose: Reads maths and logic symbols in a display equation as words: ∀ "for all", → "implies" (RI-7b).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang

/**
 * Applied only to [TextRole.EQUATION] text, first, so the language's own rules then read the numbers; the equation's
 * structure (powers, roots, fractions, derivatives) is read by [MathStructure] before the symbols. In body text these
 * symbols are rare and the general rules (SymbolRule) are enough; an equation is nearly all symbols, and an engine
 * skips or spells most of them. Word order stays the equation's ("x ≤ 3" → "x at most 3"), which is how maths is read
 * aloud in English and close enough in Hindi and Bengali, where an equation's operands are Latin letters read by the
 * English voice anyway.
 */
internal object MathSymbols {
    fun speak(text: String, lang: Lang): String {
        val table = TABLES.getValue(lang)
        val structured = MathStructure.speak(text, lang)
        val spoken = StringBuilder(structured.length * 2)
        structured.forEach { char ->
            val word = table[char] ?: COMMON[char]
            if (word == null) spoken.append(char) else spoken.append(' ').append(word).append(' ')
        }
        return BINARY_MINUS.replace(spoken, " ${MINUS.getValue(lang)} ")
    }

    /** A hyphen between spaces is a minus sign in an equation ("a - b"); in a word it stays a hyphen. */
    private val BINARY_MINUS = Regex("""(?<=\s)[-−](?=\s)""")

    private val MINUS = mapOf(Lang.EN to "minus", Lang.HI to "ऋण", Lang.BN to "বিয়োগ")

    /** Greek letters and signs read the same way in every language's equations (the English voice reads them). */
    private val COMMON = mapOf(
        'α' to "alpha", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "epsilon", 'ζ' to "zeta",
        'η' to "eta", 'θ' to "theta", 'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'ν' to "nu", 'ξ' to "xi",
        'π' to "pi", 'ρ' to "rho", 'σ' to "sigma", 'τ' to "tau", 'φ' to "phi", 'χ' to "chi", 'ψ' to "psi",
        'ω' to "omega", 'Γ' to "capital gamma", 'Δ' to "capital delta", 'Θ' to "capital theta",
        'Λ' to "capital lambda", 'Σ' to "capital sigma", 'Φ' to "capital phi", 'Ψ' to "capital psi",
        'Ω' to "capital omega", '∞' to "infinity", '∂' to "partial", '∇' to "nabla", '²' to "squared",
        '³' to "cubed",
    )

    private val ENGLISH = mapOf(
        '∀' to "for all", '∃' to "there exists", '¬' to "not", '~' to "not", '∧' to "and", '&' to "and",
        '∨' to "or", '→' to "implies", '⇒' to "implies", '⊃' to "implies", '↔' to "if and only if",
        '⇔' to "if and only if", '≡' to "is equivalent to", '⊢' to "proves", '⊨' to "entails",
        '⊥' to "contradiction", '∴' to "therefore", '∈' to "in", '∉' to "not in", '⊂' to "subset of",
        '⊆' to "subset of or equal to", '∪' to "union", '∩' to "intersection", '∅' to "the empty set",
        '=' to "equals", '≠' to "is not equal to", '≈' to "is approximately", '<' to "is less than",
        '>' to "is greater than", '≤' to "at most", '≥' to "at least", '+' to "plus", '±' to "plus or minus",
        '×' to "times", '·' to "times", '÷' to "divided by", '/' to "over", '^' to "to the power",
        '√' to "the square root of", '∑' to "the sum of", '∏' to "the product of", '∫' to "the integral of",
    )

    private val HINDI = mapOf(
        '∀' to "सभी के लिए", '∃' to "कोई ऐसा है", '¬' to "नहीं", '∧' to "और", '&' to "और", '∨' to "या",
        '→' to "तो", '⇒' to "तो", '↔' to "तभी और केवल तभी", '⇔' to "तभी और केवल तभी", '∴' to "अतः",
        '∈' to "में", '=' to "बराबर", '≠' to "बराबर नहीं", '<' to "से कम", '>' to "से अधिक",
        '≤' to "से कम या बराबर", '≥' to "से अधिक या बराबर", '+' to "धन", '×' to "गुणा", '÷' to "भाग",
        '/' to "बटा",
    )

    private val BENGALI = mapOf(
        '∀' to "সকলের জন্য", '∃' to "এমন আছে", '¬' to "নয়", '∧' to "এবং", '&' to "এবং", '∨' to "অথবা",
        '→' to "হলে", '⇒' to "হলে", '↔' to "যদি এবং কেবল যদি", '⇔' to "যদি এবং কেবল যদি", '∴' to "অতএব",
        '∈' to "এর মধ্যে", '=' to "সমান", '≠' to "সমান নয়", '<' to "এর চেয়ে কম", '>' to "এর চেয়ে বেশি",
        '≤' to "এর চেয়ে কম বা সমান", '≥' to "এর চেয়ে বেশি বা সমান", '+' to "যোগ", '×' to "গুণ", '÷' to "ভাগ",
        '/' to "বাই",
    )

    private val TABLES = mapOf(Lang.EN to ENGLISH, Lang.HI to HINDI, Lang.BN to BENGALI)
}
