// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/EnglishNormalizer.kt
// Purpose: English text normalization for every engine (ADR-012, ADR-019): ordered rules from specific to general.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.NormalizerConfig

/**
 * Applies the English rules in a fixed order. Each rule turns digits and symbols into words, so a later, more
 * general rule never re-reads what an earlier one produced; that is also why the order runs from specific
 * (links, money, clock times) to general (plain numbers). The output contains no digits a rule could read, which
 * makes normalizing twice the same as once.
 *
 * Indian-English choices: day-first dates, lakh and crore for rupees, British spelling of units, "Smt."/"Shri".
 * Devanagari and Bengali digits belong to the Indic normalizers (P3-02).
 */
class EnglishNormalizer(config: NormalizerConfig = NormalizerConfig()) : TextNormalizer {
    private val numbers = NumberReader(config)

    private val rules: List<NormalizationRule> = listOf(
        LinkRule(config.linkWord),
        CurrencyRule(config, numbers),
        TimeRule(),
        AbbreviationRule(),
        DateRule(config),
        SignRule(),
        SymbolRule(),
        QuantityRule(numbers),
        PunctuationRule(),
        RomanNumeralRule(),
        NumberRule(numbers),
    )

    override fun normalize(text: String, role: TextRole): String {
        val rewritten = rules.fold(text) { result, rule -> rule.apply(result, role) }
        return EXTRA_SPACES.replace(rewritten, " ").trim()
    }

    private companion object {
        val EXTRA_SPACES = Regex("""[ \t]{2,}""")
    }
}
