// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/LinkRule.kt
// Purpose: Reads URLs, bare web domains and e-mail addresses as one word ("link") instead of spelling them.

package io.github.skm0078.audora.speech.normalize

/**
 * Runs first, so no later rule reads the digits or dots inside an address. Trailing sentence punctuation is not
 * part of the address ("see india.gov.in." keeps its full stop). Bare domains need a known top-level domain and
 * lowercase letters, so "e.g." or "Fig.3" never qualify.
 */
internal class LinkRule(private val linkWord: String) : NormalizationRule {
    override fun apply(text: String, role: TextRole): String = LINK_PATTERN.replace(text) { match ->
        val trailing = match.value.takeLastWhile { it in TRAILING_PUNCTUATION }
        linkWord + trailing
    }

    companion object {
        /** Characters that end a sentence or a bracket after an address, never part of it. */
        internal const val TRAILING_PUNCTUATION = ".,;:!?)]}'\"’”"
        private const val EMAIL = """[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""
        private const val SCHEME_URL = """(?:https?|ftp)://\S+|www\.\S+"""
        private const val TLD = "com|org|net|in|gov|edu|io|info|co|uk|us"
        private const val DOMAIN = """[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:$TLD)(?:/\S*)?"""

        /** A URL, bare web domain or e-mail address; also what "Skip links" removes (plan item 2). */
        internal val LINK_PATTERN = Regex("""(?<![\w@.])(?:$EMAIL|$SCHEME_URL|$DOMAIN)(?![\w@])""")
    }
}
