// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/AbbreviationRule.kt
// Purpose: Expands common written abbreviations (Dr., e.g., etc., Fig. 3, pp. 10–12, Smt.) into spoken words.

package io.github.skm0078.audora.speech.normalize

/**
 * Case-sensitive on purpose: "No. 5" is "number five" but "no." is a word. Reference abbreviations (Fig., p., Ch.)
 * expand only before a number or Roman numeral, where they cannot be anything else. "St." is "Saint" before a
 * capitalised name and "Street" otherwise. Acronyms (NASA, U.S.) are left to the engine.
 */
internal class AbbreviationRule : NormalizationRule {
    override fun apply(text: String, role: TextRole): String {
        val references = REFERENCE_PATTERN.replace(text) { REFERENCES.getValue(it.value.trimEnd()) + " " }
        val saints = SAINT.replace(references) { keepSentenceStop(references, it, "Saint") }
        val streets = STREET.replace(saints) { keepSentenceStop(saints, it, "Street") }
        return GENERAL_PATTERN.replace(streets) { keepSentenceStop(streets, it, GENERAL.getValue(it.value)) }
    }

    private companion object {
        /** Expanded anywhere they stand as a word. */
        val GENERAL = mapOf(
            "Dr." to "Doctor",
            "Mr." to "Mister",
            "Mrs." to "Missus",
            "Ms." to "Miz",
            "Prof." to "Professor",
            "Jr." to "Junior",
            "Sr." to "Senior",
            "Mt." to "Mount",
            "Gen." to "General",
            "Col." to "Colonel",
            "Capt." to "Captain",
            "Lt." to "Lieutenant",
            "Sgt." to "Sergeant",
            "Rev." to "Reverend",
            "Hon'ble" to "Honourable",
            "Hon." to "Honourable",
            "Smt." to "Shrimati",
            "Sh." to "Shri",
            "Govt." to "Government",
            "Dept." to "Department",
            "Univ." to "University",
            "Ltd." to "Limited",
            "Pvt." to "Private",
            "Inc." to "Incorporated",
            "Corp." to "Corporation",
            "Bros." to "Brothers",
            "Co." to "Company",
            "Rd." to "Road",
            "Ave." to "Avenue",
            "e.g." to "for example",
            "E.g." to "For example",
            "i.e." to "that is",
            "I.e." to "That is",
            "etc." to "et cetera",
            "viz." to "namely",
            "cf." to "compare",
            "vs." to "versus",
            "vs" to "versus",
            "et al." to "and others",
            "approx." to "approximately",
            "a.k.a." to "also known as",
            "w.r.t." to "with respect to",
        )

        /** Expanded only before a number or Roman numeral. */
        val REFERENCES = mapOf(
            "No." to "number",
            "Nos." to "numbers",
            "p." to "page",
            "pp." to "pages",
            "Fig." to "figure",
            "Figs." to "figures",
            "Eq." to "equation",
            "Eqs." to "equations",
            "Sec." to "section",
            "Ch." to "chapter",
            "Vol." to "volume",
            "Art." to "article",
            "Para." to "paragraph",
        )

        private fun alternation(keys: Collection<String>): String =
            keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }

        // A word boundary that also works for keys ending in "." or "'": nothing word-like on either side.
        val GENERAL_PATTERN = Regex("""(?<![\w.])(?:${alternation(GENERAL.keys)})(?![\w])""")
        val REFERENCE_PATTERN =
            Regex("""(?<![\w.])(?:${alternation(REFERENCES.keys)})\s?(?=\d|[IVXLC]+\b)""")
        val SAINT = Regex("""(?<![\w.])St\.(?=\s+\p{Lu})""")
        val STREET = Regex("""(?<![\w.])St\.(?![\w])""")
    }
}
