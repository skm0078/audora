// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/SymbolWords.kt
// Purpose: Says the symbols of running text in the part's language: arrows, comparisons, maths signs, Greek, powers.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang

/**
 * Body text and headings hold symbols a voice cannot say: an espeak-based voice skips most of them or says nothing
 * useful, and a character-based Hindi or Bengali voice drops every one it was not trained on, so "दिल्ली → मुंबई"
 * lost its arrow and "x ≤ 5" its meaning. Display equations are [MathSymbols]' (it falls back to [word]); this is for
 * symbols in sentences. Two passes, so the number rules between them still see digits:
 * - [scripts], before the numbers: a superscript after a number, a unit or a letter of up to three letters, or a
 *   closing bracket is a power ("25 m²" "25 square metres", "10⁶", "x²", "s⁻¹"); after a longer word it is a footnote
 *   mark, left for [SpeakableText] to drop. Subscripts read as the plain characters ("H₂O" "H 2 O"), fractions as
 *   words ("2¾" "2 and three quarters"), "5 μg" as "5 micrograms" and "10 Ω" as "10 ohms".
 * - [speak], after the language's own sign, percent, unit and money rules (Hindi and Bengali run both passes after
 *   their numbers, then read a power's digits): a rule of one repeated mark ("_____", "* * *",
 *   "=====") is a pause; ASCII arrows and comparisons ("->", "<=", "!=") become their signs; "<" and ">" between two
 *   operands, "^" and a "*" between numbers are read; then every sign in the language's table is its word.
 *
 * Word order stays the text's ("x ≤ 5" "x at most 5"), as [MathSymbols] reads equations. A sign with no word in the
 * part's language (a left arrow in Hindi) is left out by [SpeakableText] rather than said in English to a Hindi voice.
 */
internal object SymbolWords {
    /** The rule that runs [scripts] on [lang]'s running text; equations have [MathStructure] instead. */
    fun scriptsRule(lang: Lang) = NormalizationRule { text, role ->
        if (role == TextRole.EQUATION) text else scripts(text, lang)
    }

    /** The rule that runs [speak] on [lang]'s running text; equations have [MathSymbols] instead. */
    fun symbolsRule(lang: Lang) = NormalizationRule { text, role ->
        if (role == TextRole.EQUATION) text else speak(text, lang)
    }

    /** [symbol]'s word in [lang], or null when it has none. */
    fun word(symbol: Char, lang: Lang): String? = TABLES.getValue(lang)[symbol]

    fun scripts(text: String, lang: Lang): String {
        if (text.none { it in SCRIPT_CHARS }) return text
        var out = text
        if (lang == Lang.EN) {
            out =
                UNIT_POWER.replace(out) {
                    "${UNIT_POWERS.getValue(it.groupValues[2])} ${AREA_UNITS.getValue(it.groupValues[1])}"
                }
            out = MICRO_UNIT.replace(out) { "micro${MICRO_UNITS.getValue(it.groupValues[1])}" }
            out = OHMS.replace(out) { "${OHM_PREFIXES.getValue(it.groupValues[1])}ohms" }
        }
        out = POWER_AFTER.replace(out) { "${it.groupValues[1]} ${power(it.groupValues[2], lang)} " }
        out =
            SUBSCRIPT_RUN.replace(out) { run ->
                " ${run.value.map(MathStructure.SUBSCRIPTS::getValue).joinToString("")} "
            }
        return FRACTION.replace(out) { fraction(it, lang) }
    }

    fun speak(text: String, lang: Lang): String {
        val table = TABLES.getValue(lang)
        // Most sentences hold none of these: no regex runs for them.
        val plain = text.none { it in table || it in ASCII_TRIGGERS } && "--" !in text
        return if (plain) text else signsAsWords(operatorsRead(text, lang, table), table)
    }

    private fun operatorsRead(text: String, lang: Lang, table: Map<Char, String>): String {
        var out = MARK_RULE.replace(text, ", ")
        out = ASCII_SIGN.replace(out) { ASCII_SIGNS.getValue(it.value).toString() }
        out = COMPARISON.replace(out) { " ${COMPARISONS.getValue(lang).getValue(it.groupValues[1].single())} " }
        out = CARET_POWER.replace(out) { " ${power(it.groupValues.drop(1).first(String::isNotEmpty), lang)} " }
        out = STAR_TIMES.replace(out, " ${table.getValue('×')} ")
        out = ABOUT.replace(out, "${table.getValue('≈')} ")
        return if (lang == Lang.EN) SHARP.replace(out, "$1 sharp") else out
    }

    private fun signsAsWords(text: String, table: Map<Char, String>): String = buildString(text.length + EXTRA_CHARS) {
        text.forEach { c -> table[c]?.let { append(' ').append(it).append(' ') } ?: append(c) }
    }

    /** "²" "squared"; "⁻¹" "to the power minus 1" (the number rules then read the digits). */
    private fun power(exponent: String, lang: Lang): String {
        val plain = exponent.map { MathStructure.SUPERSCRIPTS[it] ?: it }.filterNot { it in "+(){}" }.joinToString("")
        val signed = LEADING_MINUS.replace(plain, "${word('−', lang)} ")
        return MathStructure.powerWords(signed, lang)
    }

    private fun fraction(match: MatchResult, lang: Lang): String {
        val (whole, sign) = match.destructured
        val words = MathStructure.fractionWords(sign.single(), lang) ?: return match.value
        return if (whole.isEmpty()) " $words " else "$whole ${AND.getValue(lang)} $words "
    }

    private const val EXTRA_CHARS = 32
    private const val SUPERSCRIPT_CLASS = "[⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻ⁿⁱ⁽⁾]"
    private const val VULGAR = "½⅓⅔¼¾"

    private val SCRIPT_CHARS: Set<Char> =
        MathStructure.SUPERSCRIPTS.keys + MathStructure.SUBSCRIPTS.keys + VULGAR.toSet() + "µμΩ".toSet()

    /** "25 m²", "8 cm³": the unit's area or volume, after a number only ("in²" alone is not inches). */
    private val UNIT_POWER = Regex("""(?<=\p{Nd}\s?)(km|cm|mm|m|ft|in)([²³])(?![\p{L}\p{Nd}])""")
    private val UNIT_POWERS = mapOf("²" to "square", "³" to "cubic")
    private val AREA_UNITS = mapOf(
        "km" to "kilometres",
        "cm" to "centimetres",
        "mm" to "millimetres",
        "m" to "metres",
        "ft" to "feet",
        "in" to "inches",
    )

    /** "5 μg" (Greek mu or the micro sign) after a number. */
    private val MICRO_UNIT = Regex("""(?<=\p{Nd}\s?)[µμ](mol|g|m|l|L|s|F|A|V|W)(?![\p{L}])""")
    private val MICRO_UNITS = mapOf(
        "mol" to "moles", "g" to "grams", "m" to "metres", "l" to "litres", "L" to "litres", "s" to "seconds",
        "F" to "farads", "A" to "amperes", "V" to "volts", "W" to "watts",
    )

    /** "10 Ω", "4.7 kΩ" after a number. */
    private val OHMS = Regex("""(?<=\p{Nd}\s?)([kM]?)Ω(?![\p{L}])""")
    private val OHM_PREFIXES = mapOf("" to "", "k" to "kil", "M" to "meg")

    /** A number, a closing bracket, or a word of up to three letters (marks included: "मी") before superscripts. */
    private val POWER_AFTER = Regex("""(\p{Nd}|\)|(?<![\p{L}\p{M}])(?:\p{L}\p{M}*){1,3})($SUPERSCRIPT_CLASS+)""")
    private val SUBSCRIPT_RUN = Regex("[${MathStructure.SUBSCRIPTS.keys.joinToString("")}]+")
    private val LEADING_MINUS = Regex("""^\s*[-−]\s*""")
    private val FRACTION = Regex("""(\p{Nd}?)\s?([$VULGAR])""")

    /** What [speak]'s rules look for, besides the table's signs and a run of hyphens. */
    private const val ASCII_TRIGGERS = "<>=!^*~_#•·"

    /** Three or more of one mark, spaced or not: a rule, a blank to fill in, a scene break. */
    private val MARK_RULE = Regex("""([_=~*#•·-])(?:\s?\1){2,}""")

    /** Longest first, so "<=>" is not read as "<=" and ">". "<-" before a number is "less than minus". */
    private val ASCII_SIGNS = linkedMapOf(
        "<=>" to '⇔', "<->" to '↔', "-->" to '→', "==>" to '⇒', "->" to '→', "=>" to '⇒', "<=" to '≤', ">=" to '≥',
        "!=" to '≠', "==" to '=', "<-" to '←',
    )
    private val ASCII_SIGN =
        Regex(ASCII_SIGNS.keys.joinToString("|") { Regex.escape(it) + if (it == "<-") """(?![\d.])""" else "" })

    /** "<" and ">" between two operands ("p < 0.05", "x>y"); anything else (a quote's ">") is left out later. */
    private val COMPARISON = Regex("""(?<=[\p{L}\p{M}\p{N}\s)])\s*([<>])\s*(?=[\p{L}\p{N}(−-])""")
    private val CARET_POWER =
        Regex("""(?<=[\p{L}\p{N})])\s?\^\s?(?:\{([^\{\}]{1,12})\}|\(([^()]{1,12})\)|(-?[\p{L}\p{M}\p{N}]+))""")
    private val STAR_TIMES = Regex("""(?<=[\p{N})])\s*\*\s*(?=[\p{N}(])""")
    private val ABOUT = Regex("""~\s?(?=[\p{L}\p{N}])""")
    private val SHARP = Regex("""(?<![\p{L}])([CF])#""")

    private val AND = mapOf(Lang.EN to "and", Lang.HI to "और", Lang.BN to "এবং")

    private val COMPARISONS = mapOf(
        Lang.EN to mapOf('<' to "less than", '>' to "greater than"),
        Lang.HI to mapOf('<' to "से कम", '>' to "से अधिक"),
        Lang.BN to mapOf('<' to "এর চেয়ে কম", '>' to "এর চেয়ে বেশি"),
    )

    /** Each key's characters all read as its word. */
    private fun table(vararg groups: Pair<String, String>): Map<Char, String> =
        groups.flatMap { (chars, word) -> chars.map { it to word } }.toMap()

    private const val RIGHT_ARROWS = "→⟶➔➜➝➞⇢"
    private const val IMPLIES = "⇒⟹"
    private const val IFF = "⇔⟺"
    private const val BOTH_WAYS = "↔⟷"
    private const val UP = "↑⇑"
    private const val DOWN = "↓⇓"
    private const val TICKS = "✓✔☑"
    private const val CROSSES = "✗✘❌"

    private val ENGLISH = table(
        RIGHT_ARROWS to "to", "←⟵" to "from", IMPLIES to "implies", IFF to "if and only if", BOTH_WAYS to "and",
        "⇌⇄" to "is in equilibrium with", UP to "up", DOWN to "down",
        "≤⩽" to "at most", "≥⩾" to "at least", "≠" to "not equal to", "≈≃∼" to "approximately",
        "≅" to "congruent to", "≡" to "is equivalent to", "∝" to "proportional to", "≪" to "much less than",
        "≫" to "much greater than",
        "+" to "plus", "−" to "minus", "±" to "plus or minus", "∓" to "minus or plus", "×" to "times",
        "÷" to "divided by", "=" to "equals", "√" to "the square root of", "∛" to "the cube root of",
        "∑" to "the sum of", "∏" to "the product of", "∫" to "the integral of", "∂" to "partial", "∇" to "nabla",
        "∞" to "infinity", "%" to "percent", "‰" to "per mille", "°" to "degrees", "℃" to "degrees Celsius",
        "℉" to "degrees Fahrenheit",
        "∈" to "in", "∉" to "not in", "⊂⊆" to "subset of", "∪" to "union", "∩" to "intersection",
        "∅" to "the empty set", "∀" to "for all", "∃" to "there exists", "¬" to "not", "∧" to "and", "∨" to "or",
        "∴" to "therefore", "∵" to "because", "∠" to "angle", "⊥" to "perpendicular to", "∥" to "parallel to",
        "△" to "triangle", "§" to "section", "¶" to "paragraph", "©" to "copyright", "№" to "number",
        TICKS to "tick", CROSSES to "cross",
        "α" to "alpha", "β" to "beta", "γ" to "gamma", "δ∆" to "delta", "εϵ" to "epsilon", "ζ" to "zeta",
        "η" to "eta", "θϑ" to "theta", "κ" to "kappa", "λ" to "lambda", "μµ" to "mu", "ν" to "nu", "ξ" to "xi",
        "π" to "pi", "ρ" to "rho", "σ" to "sigma", "τ" to "tau", "φϕ" to "phi", "χ" to "chi", "ψ" to "psi",
        "ω" to "omega", "Γ" to "capital gamma", "Θ" to "capital theta", "Λ" to "capital lambda",
        "Π" to "capital pi", "Φ" to "capital phi", "Ψ" to "capital psi",
        // Said by name, not "capital": "Δt" is "delta t", "Σ" "sigma", "Ω" "omega" (ohms after a number, above).
        "Δ" to "delta", "Σ" to "sigma", "Ω" to "omega",
    )

    private val HINDI = table(
        RIGHT_ARROWS to "से", IMPLIES to "तो", IFF to "तभी और केवल तभी", BOTH_WAYS to "और", UP to "बढ़ा",
        DOWN to "घटा",
        "≤⩽" to "से कम या बराबर", "≥⩾" to "से अधिक या बराबर", "≠" to "बराबर नहीं", "≈≃∼" to "लगभग",
        "≡" to "सर्वसम", "∝" to "समानुपाती",
        "+" to "धन", "−" to "ऋण", "±" to "धन ऋण", "×" to "गुणा", "÷" to "भाग", "=" to "बराबर",
        "√" to "वर्गमूल", "∑" to "योग", "∞" to "अनंत", "°" to "डिग्री", "℃" to "डिग्री सेल्सियस",
        "∈" to "में", "∴" to "अतः", "∵" to "क्योंकि", "∠" to "कोण", "⊥" to "लंब", "∥" to "समांतर",
        "△" to "त्रिभुज", "§" to "धारा", "©" to "कॉपीराइट", TICKS to "सही", CROSSES to "गलत",
        "α" to "अल्फ़ा", "β" to "बीटा", "γΓ" to "गामा", "δ∆Δ" to "डेल्टा", "εϵ" to "एप्सिलॉन", "ζ" to "ज़ीटा",
        "η" to "ईटा", "θϑΘ" to "थीटा", "κ" to "कप्पा", "λΛ" to "लैम्डा", "μµ" to "म्यू", "ν" to "न्यू",
        "ξ" to "ज़ाई", "πΠ" to "पाई", "ρ" to "रो", "σΣ" to "सिग्मा", "τ" to "टाउ", "φϕΦ" to "फ़ाई", "χ" to "काई",
        "ψΨ" to "साई", "ωΩ" to "ओमेगा",
    )

    private val BENGALI = table(
        RIGHT_ARROWS to "থেকে", IMPLIES to "হলে", IFF to "যদি এবং কেবল যদি", BOTH_WAYS to "এবং", UP to "বৃদ্ধি",
        DOWN to "হ্রাস",
        "≤⩽" to "এর চেয়ে কম বা সমান", "≥⩾" to "এর চেয়ে বেশি বা সমান", "≠" to "সমান নয়", "≈≃∼" to "প্রায়",
        "≡" to "সর্বসম", "∝" to "সমানুপাতিক",
        "+" to "যোগ", "−" to "বিয়োগ", "±" to "যোগ বিয়োগ", "×" to "গুণ", "÷" to "ভাগ", "=" to "সমান",
        "√" to "বর্গমূল", "∑" to "যোগফল", "∞" to "অসীম", "°" to "ডিগ্রি", "℃" to "ডিগ্রি সেলসিয়াস",
        "∈" to "এর মধ্যে", "∴" to "অতএব", "∵" to "যেহেতু", "∠" to "কোণ", "⊥" to "লম্ব", "∥" to "সমান্তরাল",
        "△" to "ত্রিভুজ", "§" to "ধারা", "©" to "কপিরাইট", TICKS to "ঠিক", CROSSES to "ভুল",
        "α" to "আলফা", "β" to "বিটা", "γΓ" to "গামা", "δ∆Δ" to "ডেল্টা", "εϵ" to "এপসাইলন", "ζ" to "জিটা",
        "η" to "ইটা", "θϑΘ" to "থিটা", "κ" to "কাপ্পা", "λΛ" to "ল্যামডা", "μµ" to "মিউ", "ν" to "নিউ",
        "ξ" to "জাই", "πΠ" to "পাই", "ρ" to "রো", "σΣ" to "সিগমা", "τ" to "টাউ", "φϕΦ" to "ফাই", "χ" to "কাই",
        "ψΨ" to "সাই", "ωΩ" to "ওমেগা",
    )

    private val TABLES = mapOf(Lang.EN to ENGLISH, Lang.HI to HINDI, Lang.BN to BENGALI)
}
