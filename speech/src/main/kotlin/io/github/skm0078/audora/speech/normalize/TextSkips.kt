// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TextSkips.kt
// Purpose: The skip switches (plan item 2): leaves out (parentheses), [brackets] and links before a sentence is read.

package io.github.skm0078.audora.speech.normalize

/**
 * Runs on the written text before normalization, so the page still shows everything. A parenthesis counts only when it
 * opens a word ("the river (see map) rose"), so "f(x)" and "2(a)" stay; nested ones go from the inside out. Brackets
 * go wherever they are ("[sic]", "word[3]"). A link keeps the punctuation after it. Spaces left behind are tidied so
 * the voice does not pause on them.
 */
class TextSkips(private val parentheses: Boolean, private val brackets: Boolean, private val links: Boolean) {
    val isNone: Boolean get() = !parentheses && !brackets && !links

    fun apply(text: String): String {
        if (isNone) return text
        var out = text
        if (links) {
            out =
                LinkRule.LINK_PATTERN.replace(out) {
                    it.value.takeLastWhile { c -> c in LinkRule.TRAILING_PUNCTUATION }
                }
        }
        if (parentheses) out = removeNested(out, PARENTHESIS)
        if (brackets) out = removeNested(out, BRACKET)
        return out.replace(SPACE_BEFORE_MARK, "$1").replace(SPACES, " ").trim()
    }

    /** Removes the innermost matches until none is left, so "(a (b) c)" goes whole; bounded by the text's length. */
    private fun removeNested(text: String, pattern: Regex): String {
        var current = text
        while (true) {
            val next = pattern.replace(current, "$1")
            if (next == current) return current
            current = next
        }
    }

    private companion object {
        val PARENTHESIS = Regex("""(^|\s)\([^()]*\)""")
        val BRACKET = Regex("""()\s*\[[^\[\]]*]""")
        val SPACE_BEFORE_MARK = Regex("""\s+([.,;:!?।॥])""")
        val SPACES = Regex("""\s{2,}""")
    }
}
