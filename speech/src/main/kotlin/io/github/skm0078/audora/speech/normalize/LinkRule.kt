// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/LinkRule.kt
// Purpose: Reads URLs, bare web domains and e-mail addresses as one word ("link") instead of spelling them.

package io.github.skm0078.audora.speech.normalize

/**
 * Runs first, so no later rule reads the digits or dots inside an address. Trailing sentence punctuation is not
 * part of the address ("see india.gov.in." keeps its full stop). Bare domains need a known top-level domain and
 * lowercase letters, so "e.g." or "Fig.3" never qualify.
 */
internal class LinkRule(private val linkWord: String) : NormalizationRule {
    override fun apply(text: String, role: TextRole): String = LINK.replace(text) { match ->
        val trailing = match.value.takeLastWhile { it in TRAILING_PUNCTUATION }
        linkWord + trailing
    }

    private companion object {
        const val TRAILING_PUNCTUATION = ".,;:!?)]}'\"’”"
        const val EMAIL = """[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""
        const val SCHEME_URL = """(?:https?|ftp)://\S+|www\.\S+"""
        const val DOMAIN = """[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|org|net|in|gov|edu|io|info|co|uk|us)(?:/\S*)?"""
        val LINK = Regex("""(?<![\w@.])(?:$EMAIL|$SCHEME_URL|$DOMAIN)(?![\w@])""")
    }
}
