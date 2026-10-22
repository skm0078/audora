// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizWords.kt
// Purpose: Which words may be a quiz blank: not a common function word, not a malformed (OCR-broken) token (item 70).

package io.github.skm0078.audora.core.quiz

/**
 * Common function words of four letters or more that rarity within a few highlights can still pick (a word used
 * once in five highlights is "rare" there), compared as NFC lower case. Short lists by design: they only stop the
 * blanks a reader would find pointless.
 */
internal val QUIZ_STOP_WORDS: Set<String> = setOf(
    // English
    "about", "after", "because", "before", "being", "could", "other", "should", "their", "there", "these",
    "those", "through", "where", "which", "while", "would",
    // Hindi
    "उन्होंने", "लेकिन", "इसलिए", "क्योंकि", "जिसमें", "द्वारा", "उनके", "उनकी", "उसके", "इसके", "अपने", "अपनी",
    "किसी", "होता", "होती", "होते", "सकता", "सकती", "सकते", "जाता", "जाती", "जाते", "करने", "करते", "यहाँ", "वहाँ",
    "जैसे", "अधिक", "बहुत",
    // Bengali
    "কিন্তু", "তাহলে", "যেখানে", "সেখানে", "এখানে", "তারপর", "যদিও", "অথবা", "কারণ", "তাদের", "আমাদের", "তোমার",
    "আমার", "হয়েছে", "করেছে", "করতে", "থেকে", "জন্য", "সঙ্গে", "মধ্যে", "কিছু", "সবাই", "অনেক",
)

/**
 * A token no one writes: a Devanagari or Bengali vowel sign right after an independent vowel or after another vowel
 * sign (in NFC, where Bengali ো and ৌ are one sign). OCR misreads look like this and would otherwise win on rarity.
 */
internal fun isMalformedIndic(nfc: String): Boolean {
    var previous = 0
    var at = 0
    while (at < nfc.length) {
        val current = nfc.codePointAt(at)
        if (isVowelSign(current) && (isVowelSign(previous) || isIndependentVowel(previous))) return true
        previous = current
        at += Character.charCount(current)
    }
    return false
}

private fun isVowelSign(c: Int): Boolean = c in VOWEL_SIGNS

private fun isIndependentVowel(c: Int): Boolean = c in INDEPENDENT_VOWELS

private fun codes(vararg ranges: CharRange): Set<Int> = ranges.flatMap { range -> range.map { it.code } }.toSet()

private val VOWEL_SIGNS = codes(
    '\u093A'..'\u093B',
    '\u093E'..'\u094C',
    '\u094E'..'\u094F',
    '\u0955'..'\u0957',
    '\u0962'..'\u0963',
    '\u09BE'..'\u09CC',
    '\u09D7'..'\u09D7',
    '\u09E2'..'\u09E3',
)

private val INDEPENDENT_VOWELS = codes(
    '\u0904'..'\u0914',
    '\u0960'..'\u0961',
    '\u0972'..'\u0977',
    '\u0985'..'\u0994',
    '\u09E0'..'\u09E1',
)
