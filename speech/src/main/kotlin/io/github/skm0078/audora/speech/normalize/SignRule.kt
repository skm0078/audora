// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/SignRule.kt
// Purpose: Reads a leading minus sign ("-5", "−3.2 °C") as "minus".

package io.github.skm0078.audora.speech.normalize

/**
 * Only a sign that starts a word counts: "COVID-19", "2019-20" and "pages 10-12" keep their hyphens for the range
 * rule or the engine. Runs before quantities so "-5 °C" becomes "minus five degrees Celsius".
 */
internal class SignRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String = SIGN.replace(text, "minus ")

    private companion object {
        val SIGN = Regex("""(?:^|(?<=[\s(\[]))[-−](?=\d)""")
    }
}
