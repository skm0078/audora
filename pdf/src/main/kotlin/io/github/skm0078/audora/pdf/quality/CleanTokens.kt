// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/CleanTokens.kt
// Purpose: The symbol-soup signal: the share of a sentence's tokens that look like words or numbers (RI-2a).

package io.github.skm0078.audora.pdf.quality

/**
 * A token (text between spaces) is clean when, after the punctuation around it is set aside ("(2.", "word,", "$7.50"),
 * it is only letters, marks and digits, with ".,:/-'" allowed inside ("2.1", "www.cdc.gov/vsafe", "don't"). It is
 * not clean when it holds any other symbol ("=~", "rT{Sr", "o™"), a capital straight after a small letter ("PTfuqT":
 * OCR noise far more often than a real word), or Devanagari or Bengali next to Latin letters or ASCII digits
 * ("8प्रो", "6०08": an English page read with the Hindi model). A lone punctuation mark ("।", "—") is neutral.
 */
internal object CleanTokens {
    private const val INNER = ".,:/-'’–"
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D

    /** The share of [text]'s characters in clean tokens, 0–1; 1 for text with no token that counts. */
    fun share(text: String): Float {
        var clean = 0
        var counted = 0
        for (token in text.split(' ', '\n', '\t', '\r', '\u00A0')) {
            val length = token.codePointCount(0, token.length)
            val core = token.trim { isOuter(it.code) }
            // Empty tokens and a lone punctuation mark do not count either way.
            if (core.isEmpty() && length <= 1) continue
            counted += length
            if (core.isNotEmpty() && isClean(core)) clean += length
        }
        return if (counted == 0) 1f else clean.toFloat() / counted
    }

    private fun isOuter(cp: Int): Boolean = cp == '%'.code || when (Character.getType(cp)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        -> true

        else -> false
    }

    private fun isClean(core: String): Boolean {
        var latin = false
        var indic = false
        var previous = 0
        var i = 0
        while (i < core.length) {
            val cp = core.codePointAt(i)
            i += Character.charCount(cp)
            val allowed =
                Words.isWordChar(cp) || Character.isDigit(cp) || cp == ZWNJ || cp == ZWJ || cp.toChar() in INNER
            if (!allowed || (Character.isUpperCase(cp) && Character.isLowerCase(previous))) return false
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latin = true
                Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI -> indic = true
                else -> if (cp in '0'.code..'9'.code) latin = true
            }
            previous = cp
        }
        return !(latin && indic)
    }
}
