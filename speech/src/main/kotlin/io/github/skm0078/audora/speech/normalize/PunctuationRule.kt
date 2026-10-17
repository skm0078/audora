// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/PunctuationRule.kt
// Purpose: Speaks the punctuation a voice would skip or misread: "and/or", "24/7", a bare "°C" (RI-6).

package io.github.skm0078.audora.speech.normalize

/**
 * Runs after links, dates and quantities (so "km/h", "15/08/1947" and "37 °C" are already words) and before plain
 * numbers. A slash after a unit of up to 4 letters that follows a number reads "per" ("60 km/hr", "5 mg/kg"). A slash
 * between two words of two letters or more reads "or" ("and/or", "yes/no"); a one-letter side ("w/o", "A/C") is an
 * abbreviation and is left to the engine. A slash between numbers that are not a date ("24/7", "3/4") reads "slash",
 * so no slash is left for a second pass to read differently. Ranges ("10–20") are the number rule's; "?" and "!" are
 * never touched, so the voice still rises and exclaims.
 */
internal class PunctuationRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String =
        REPLACEMENTS.fold(text) { result, (pattern, spoken) -> pattern.replace(result, spoken) }

    private companion object {
        val REPLACEMENTS = listOf(
            Regex("""\band/or\b""") to "and or",
            // A slash after a short unit that follows a number is a rate: "60 km/hr", "5 mg/kg", "100 mg/dL".
            Regex("""(?<=\d\s?\p{L}{1,4})/(?=\p{L})""") to " per ",
            Regex("""(?<=\b\p{L}{2,20})/(?=\p{L}{2,20}\b)""") to " or ",
            Regex("""(?<=\d)\s?/\s?(?=\d)""") to " slash ",
            Regex("""(?<![\w.])°C\b""") to "degrees Celsius",
            Regex("""(?<![\w.])°F\b""") to "degrees Fahrenheit",
        )
    }
}
