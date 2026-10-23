// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/MathStructure.kt
// Purpose: Reads an equation's structure as a teacher would: powers, roots, fractions, derivatives (plan item 66).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang

/**
 * Runs before [MathSymbols] on [TextRole.EQUATION] text, so what is left for it is single symbols. Rules for common
 * notation, in this order (each sees the output of the ones before):
 * - Unicode superscripts and subscripts become `^{…}` and `_{…}`, so "x²", "x^2" and "x^{2}" read alike.
 * - Vulgar fractions ("½") become words; letters run together ("xy", "dx") are spelled apart unless a known word.
 * - Derivatives: "dy/dx" is "d y by d x", "d²y/dx²" "d squared y by d x squared", ∂ "partial".
 * - "(a+b)/(c+d)" is "the fraction a plus b over c plus d"; "√(x+1)" "the square root of x plus 1"; the pause (a
 *   comma) after a group marks where it ends, as a reader's voice does.
 * - Powers: "squared", "cubed", else "to the power n"; subscripts "x sub i"; "|x|" the absolute value; "n!" factorial.
 * - Functions by name ("sin" "sine", "ln(x)" "natural log of x"), "f(x)" "f of x", "f′(x)" "f prime of x", and "→"
 *   after "lim" is "approaches", not "implies". A one-letter group "(x)" is the letter, never a roman numeral.
 * - A minus sign written without spaces ("n-1", "-x") is spaced so [MathSymbols] reads it; a hyphen between two words
 *   ("well-known") stays.
 *
 * Hindi and Bengali use their own words for the structure, in their own order ("x का वर्ग"); function names stay
 * English, as Indian textbooks say them. What these rules can't parse (nested groups, matrices, limits under a sum) is
 * read symbol by symbol as before.
 */
internal object MathStructure {
    fun speak(text: String, lang: Lang): String {
        val words = WORDS.getValue(lang)
        var out = scripts(text)
        out = LETTER_RUN.replace(out) { run -> if (run.value in KNOWN_WORDS) run.value else run.value.spelled() }
        out = VULGAR.replace(out) { " ${words.vulgar[it.value.single()] ?: it.value} " }
        out = DERIVATIVE.replace(out) { derivative(it, words) }
        out =
            GROUP_FRACTION.replace(out) {
                " ${words[Term.FRACTION]} ${it.groupValues[1]} ${words[Term.OVER]} ${it.groupValues[2]}, "
            }
        out =
            GROUP_ROOT.replace(out) { " ${words.root(it.groupValues[1])} ${it.groupValues.drop(2).joinToString("")}, " }
        out = BARE_ROOT.replace(out) { " ${words.root(it.groupValues[1])} ${it.groupValues[2]} " }
        out = POWER.replace(out) { power(it.groupValues.drop(1).first(String::isNotEmpty), words) }
        out = SUBSCRIPT.replace(out) { " ${words[Term.SUB]} ${it.groupValues.drop(1).first(String::isNotEmpty)} " }
        out = ABSOLUTE.replace(out) { " ${words[Term.ABSOLUTE]} ${it.groupValues[1]}, " }
        out = FACTORIAL.replace(out) { "${it.groupValues[1]} ${words[Term.FACTORIAL]} " }
        val limit = LIMIT.containsMatchIn(out)
        out = FUNCTION.replace(out) { named(it, words) }
        out = APPLIED.replace(out) { applied(it, words) }
        out = LETTER_GROUP.replace(out) { " ${it.groupValues[1]} " }
        if (limit) out = ARROW.replace(out, " ${words[Term.APPROACHES]} ")
        out = ELLIPSIS.replace(out, " ${words[Term.AND_SO_ON]} ")
        val spaced = DIGIT_LETTER.replace(out, " ")
        val minus = TIGHT_MINUS.replace(spaced) { if (betweenWords(spaced, it.range.first)) it.value else " − " }
        return UNARY_MINUS.replace(minus, " − ")
    }

    private fun String.spelled(): String = toList().joinToString(" ")

    /** Groups: the top's d or ∂, its order, the variable, the bottom's d or ∂, its variable, its order. */
    private fun derivative(match: MatchResult, words: Words): String {
        val g = match.groupValues
        fun order(value: String) = value.takeIf { it.isNotEmpty() }?.let { " ${words.powerOf(it)}" }.orEmpty()
        val top = "${words.d(g[TOP_D])}${order(g[TOP_ORDER])} ${g[TOP_OF]}"
        return " $top ${words[Term.BY]} ${words.d(g[BOTTOM_D])} ${g[BOTTOM_BY]}${order(g[BOTTOM_ORDER])} "
    }

    private fun power(exponent: String, words: Words): String {
        val value = exponent.trim()
        val grouped = value.any { !it.isLetterOrDigit() }
        return " ${words.powerOf(value)}${if (grouped) ", " else " "}"
    }

    /** "sin" is "sine"; "sin(x)" "sine of x". */
    private fun named(match: MatchResult, words: Words): String {
        val name = FUNCTION_WORDS.getValue(match.groupValues[1])
        return if (match.groupValues[2].isEmpty() ||
            words[Term.OF].isEmpty()
        ) {
            " $name ${match.groupValues[2]}"
        } else {
            " $name ${words[Term.OF]} ("
        }
    }

    private fun applied(match: MatchResult, words: Words): String {
        val prime = if (match.groupValues[2].isEmpty()) "" else " ${words[Term.PRIME]}"
        return "${match.groupValues[1]}$prime ${words[Term.OF]} ("
    }

    /** Superscript and subscript characters as `^{…}` and `_{…}`. */
    private fun scripts(text: String): String {
        val out = StringBuilder(text.length + EXTRA_CHARS)
        var i = 0
        while (i < text.length) {
            val table = when (text[i]) {
                in SUPERSCRIPTS -> SUPERSCRIPTS
                in SUBSCRIPTS -> SUBSCRIPTS
                else -> null
            }
            if (table == null) {
                out.append(text[i++])
                continue
            }
            val run = text.drop(i).takeWhile { it in table }
            out.append(if (table === SUPERSCRIPTS) "^{" else "_{").append(run.map(table::getValue).joinToString(""))
                .append('}')
            i += run.length
        }
        return out.toString()
    }

    /** A hyphen with at least two letters on each side is a hyphen ("well-known"), not a minus. */
    private fun betweenWords(text: String, at: Int): Boolean {
        val before = text.take(at).takeLastWhile(Char::isLetter).length
        val after = text.drop(at + 1).takeWhile(Char::isLetter).length
        return before >= 2 && after >= 2
    }

    private const val EXTRA_CHARS = 16
    private const val TOP_D = 1
    private const val TOP_ORDER = 2
    private const val TOP_OF = 3
    private const val BOTTOM_D = 4
    private const val BOTTOM_BY = 5
    private const val BOTTOM_ORDER = 6

    private val SUPERSCRIPTS = mapOf(
        '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4', '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8',
        '⁹' to '9', '⁺' to '+', '⁻' to '-', 'ⁿ' to 'n', 'ⁱ' to 'i', '⁽' to '(', '⁾' to ')',
    )
    private val SUBSCRIPTS = mapOf(
        '₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4', '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8',
        '₉' to '9', '₊' to '+', '₋' to '-', 'ₙ' to 'n', 'ᵢ' to 'i', 'ⱼ' to 'j', 'ₖ' to 'k', 'ₓ' to 'x',
    )

    private val VULGAR = Regex("[½⅓⅔¼¾]")
    private val LETTER_RUN = Regex("""(?<![A-Za-z])[a-z]{2,3}(?![A-Za-z])""")

    /**
     * Lowercase words an equation may hold that are read as words, not letters. Not "at", "is", "as", "to", "by" or
     * "on": in an equation those are nearly always two variables ("v = u + at").
     */
    private val KNOWN_WORDS = setOf(
        "and", "or", "of", "in", "the", "for", "all", "not", "mod", "if", "iff",
        "max", "min", "log", "ln", "sin", "cos", "tan", "sec", "cot", "csc", "exp", "lim", "det", "gcd", "lcm", "arg",
        "dim", "ker", "sup", "inf",
    )
    private val DERIVATIVE = Regex(
        """(?<![A-Za-z])([d∂])(?:\^\{?(\d)\}?)?\s*([A-Za-z])\s*/\s*([d∂])\s*([A-Za-z])(?:\^\{?(\d)\}?)?(?![A-Za-z])""",
    )
    private val GROUP_FRACTION = Regex("""\(([^()]+)\)\s*/\s*\(([^()]+)\)""")
    private val GROUP_ROOT = Regex("""([√∛∜])\s*(?:\(([^()]+)\)|\{([^{}]+)\})""")
    private val BARE_ROOT = Regex("""([√∛∜])\s*([A-Za-z0-9]+)""")
    private val POWER = Regex("""\^\s*(?:\{([^{}]+)\}|\(([^()]+)\)|(-?\d+|-?[A-Za-z]))""")
    private val SUBSCRIPT = Regex("""_\s*(?:\{([^{}]+)\}|([A-Za-z0-9]+))""")
    private val ABSOLUTE = Regex("""\|([^|]+)\|""")
    private val FACTORIAL = Regex("""([A-Za-z0-9)])!""")
    private val LIMIT = Regex("""(?<![A-Za-z])lim(?![A-Za-z])""")
    private val ARROW = Regex("""→|->""")
    private val ELLIPSIS = Regex("""…|⋯|\.\.\.|⋅⋅⋅""")
    private val DIGIT_LETTER = Regex("""(?<=\d)(?=[A-Za-z])""")
    private val TIGHT_MINUS = Regex("""(?<=[A-Za-z0-9)}\]])[-−](?=[A-Za-z0-9({\[])""")
    private val UNARY_MINUS = Regex("""(?:(?<=^)|(?<=[\s(=]))[-−](?=[A-Za-z0-9(])""")

    /** Longest first, so "sinh" is not read as "sin" and "h". */
    private val FUNCTION_WORDS = linkedMapOf(
        "arcsin" to "arc sine", "arccos" to "arc cosine", "arctan" to "arc tangent", "sinh" to "hyperbolic sine",
        "cosh" to "hyperbolic cosine", "tanh" to "hyperbolic tangent", "cosec" to "cosecant", "sin" to "sine",
        "cos" to "cosine", "tan" to "tangent", "cot" to "cotangent", "sec" to "secant", "csc" to "cosecant",
        "ln" to "natural log", "log" to "log", "exp" to "exponential", "lim" to "the limit as", "max" to "the maximum",
        "min" to "the minimum", "det" to "the determinant",
    )
    private val FUNCTION = Regex("""(?<![A-Za-z])(${FUNCTION_WORDS.keys.joinToString("|")})(?![A-Za-z])(\s*\()?""")

    /** A group of one letter, "(x)", is the letter, never the roman numeral a label "(x)" would be. */
    private val LETTER_GROUP = Regex("""\(\s*([A-Za-z])\s*\)""")
    private val APPLIED = Regex("""(?<![A-Za-z])([fghFGHP])([′']?)\s*\(""")

    private enum class Term {
        SQUARED,
        CUBED,
        POWER,
        SUB,
        OVER,
        FRACTION,
        SQUARE_ROOT,
        CUBE_ROOT,
        FOURTH_ROOT,
        ABSOLUTE,
        FACTORIAL,
        PRIME,
        OF,
        APPROACHES,
        AND_SO_ON,
        BY,
        PARTIAL,
    }

    /** The words for the structure, per language: every [Term], and the vulgar fractions. */
    private class Words(private val terms: Map<Term, String>, val vulgar: Map<Char, String>) {
        init {
            require(terms.keys == Term.entries.toSet()) { "Missing words: ${Term.entries - terms.keys}" }
        }

        operator fun get(term: Term): String = terms.getValue(term)

        fun powerOf(value: String): String = when (value) {
            "2" -> get(Term.SQUARED)
            "3" -> get(Term.CUBED)
            else -> "${get(Term.POWER)} $value"
        }

        fun root(sign: String): String = when (sign) {
            "∛" -> get(Term.CUBE_ROOT)
            "∜" -> get(Term.FOURTH_ROOT)
            else -> get(Term.SQUARE_ROOT)
        }

        fun d(sign: String): String = if (sign == "∂") get(Term.PARTIAL) else "d"
    }

    private val WORDS = mapOf(
        Lang.EN to Words(
            terms = mapOf(
                Term.SQUARED to "squared",
                Term.CUBED to "cubed",
                Term.POWER to "to the power",
                Term.SUB to "sub",
                Term.OVER to "over",
                Term.FRACTION to "the fraction",
                Term.SQUARE_ROOT to "the square root of",
                Term.CUBE_ROOT to "the cube root of",
                Term.FOURTH_ROOT to "the fourth root of",
                Term.ABSOLUTE to "the absolute value of",
                Term.FACTORIAL to "factorial",
                Term.PRIME to "prime",
                Term.OF to "of",
                Term.APPROACHES to "approaches",
                Term.AND_SO_ON to "and so on",
                Term.BY to "by",
                Term.PARTIAL to "partial",
            ),
            vulgar = mapOf(
                '½' to "one half",
                '⅓' to "one third",
                '⅔' to "two thirds",
                '¼' to "one quarter",
                '¾' to "three quarters",
            ),
        ),
        Lang.HI to Words(
            terms = mapOf(
                Term.SQUARED to "का वर्ग",
                Term.CUBED to "का घन",
                Term.POWER to "की घात",
                Term.SUB to "सब",
                Term.OVER to "बटा",
                Term.FRACTION to "भिन्न",
                Term.SQUARE_ROOT to "वर्गमूल",
                Term.CUBE_ROOT to "घनमूल",
                Term.FOURTH_ROOT to "चतुर्थ मूल",
                Term.ABSOLUTE to "मापांक",
                Term.FACTORIAL to "फ़ैक्टोरियल",
                Term.PRIME to "प्राइम",
                Term.OF to "",
                Term.APPROACHES to "की ओर",
                Term.AND_SO_ON to "इत्यादि",
                Term.BY to "बटा",
                Term.PARTIAL to "आंशिक",
            ),
            vulgar = mapOf(
                '½' to "आधा",
                '⅓' to "एक तिहाई",
                '⅔' to "दो तिहाई",
                '¼' to "एक चौथाई",
                '¾' to "तीन चौथाई",
            ),
        ),
        Lang.BN to Words(
            terms = mapOf(
                Term.SQUARED to "এর বর্গ",
                Term.CUBED to "এর ঘন",
                Term.POWER to "এর ঘাত",
                Term.SUB to "সাব",
                Term.OVER to "বাই",
                Term.FRACTION to "ভগ্নাংশ",
                Term.SQUARE_ROOT to "বর্গমূল",
                Term.CUBE_ROOT to "ঘনমূল",
                Term.FOURTH_ROOT to "চতুর্থ মূল",
                Term.ABSOLUTE to "পরমমান",
                Term.FACTORIAL to "ফ্যাক্টোরিয়াল",
                Term.PRIME to "প্রাইম",
                Term.OF to "",
                Term.APPROACHES to "এর দিকে",
                Term.AND_SO_ON to "ইত্যাদি",
                Term.BY to "বাই",
                Term.PARTIAL to "আংশিক",
            ),
            vulgar = mapOf(
                '½' to "অর্ধেক",
                '⅓' to "এক তৃতীয়াংশ",
                '⅔' to "দুই তৃতীয়াংশ",
                '¼' to "এক চতুর্থাংশ",
                '¾' to "তিন চতুর্থাংশ",
            ),
        ),
    )
}
