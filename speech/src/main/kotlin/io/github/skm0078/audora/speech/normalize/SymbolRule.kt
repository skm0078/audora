// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/SymbolRule.kt
// Purpose: Speaks the symbols that engines skip or misread: &, #5, 3 × 4, +, =, ~10, ±, a stray @.

package io.github.skm0078.audora.speech.normalize

/** Runs after links (so e-mail "@" is gone) and before quantities and numbers, which it needs to see as digits. */
internal class SymbolRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String =
        REPLACEMENTS.fold(text) { result, (pattern, spoken) -> pattern.replace(result, spoken) }

    private companion object {
        val REPLACEMENTS = listOf(
            Regex("""\s*&\s*""") to " and ",
            Regex("""#\s?(?=\d)""") to "number ",
            Regex("""(?<=\d)\s?[×x]\s?(?=\d)""") to " by ",
            Regex("""(?<=[\d\s])\+(?=[\s\d])""") to " plus ",
            Regex("""(?<=\s)=(?=\s)""") to "equals",
            Regex("""~\s?(?=\d)""") to "about ",
            Regex("""\s?±\s?""") to " plus or minus ",
            Regex("""\s@\s""") to " at ",
        )
    }
}
