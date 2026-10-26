// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/PhoneLanguages.kt
// Purpose: Languages beyond English, Hindi and Bengali, read by the phone's own voices (ADR 2026-10-26): which
//          script each is written in, and which language a script Audora has no voice of its own for is read in.

package io.github.skm0078.audora.core.model

import java.lang.Character.UnicodeScript
import java.util.Locale

/**
 * The three [Lang]s keep their own voices, normalizers and sentence rules. Any other language is a "phone language":
 * a BCP 47 primary language tag ("fr", "ta", "ja") read whole by the phone's offline text-to-speech voice, which says
 * numbers and abbreviations in its own language. A tag comes from the book's language (what its file says, or what
 * the listener set in Edit details) or, for scripts none of the three is written in, from [defaultFor].
 *
 * Pure and deterministic, no Android: the tables cover the languages Android's engines commonly offer; a language not
 * named in [WRITTEN_IN] is taken to be written in Latin letters, as nearly all the rest are (French, Swahili, Malay).
 */
object PhoneLanguages {
    /** Tags Java and Android still give for three languages, and the ones they mean ("iw" is Hebrew). */
    private val LEGACY = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

    /**
     * The language a script's letters are read in when the book says nothing else. Han reads as Chinese unless its
     * sentence holds kana ([CJK_WITH_KANA]); Arabic letters as Arabic, Cyrillic as Russian (a book in Urdu or
     * Ukrainian says so in its language). Greek is not here: in a book that is not in Greek its letters are maths
     * ("αβγ", "Δθ"), which the three languages' voices read as symbols.
     */
    private val DEFAULTS: Map<UnicodeScript, String> = mapOf(
        UnicodeScript.TAMIL to "ta",
        UnicodeScript.TELUGU to "te",
        UnicodeScript.KANNADA to "kn",
        UnicodeScript.MALAYALAM to "ml",
        UnicodeScript.GUJARATI to "gu",
        UnicodeScript.GURMUKHI to "pa",
        UnicodeScript.ORIYA to "or",
        UnicodeScript.SINHALA to "si",
        UnicodeScript.ARABIC to "ar",
        UnicodeScript.HEBREW to "he",
        UnicodeScript.CYRILLIC to "ru",
        UnicodeScript.ARMENIAN to "hy",
        UnicodeScript.GEORGIAN to "ka",
        UnicodeScript.THAI to "th",
        UnicodeScript.LAO to "lo",
        UnicodeScript.KHMER to "km",
        UnicodeScript.MYANMAR to "my",
        UnicodeScript.ETHIOPIC to "am",
        UnicodeScript.HANGUL to "ko",
        UnicodeScript.HAN to "zh",
    )

    /** What Han letters are read in when their sentence also holds hiragana or katakana. */
    const val CJK_WITH_KANA = "ja"

    /** Languages not written in Latin letters, beyond the scripts' own defaults above. */
    private val WRITTEN_IN: Map<String, UnicodeScript> = DEFAULTS.entries.associate { (script, tag) -> tag to script } +
        listOf("hi", "mr", "ne", "sa", "kok", "mai", "bho", "doi", "brx").associateWith { UnicodeScript.DEVANAGARI } +
        listOf("bn", "as", "mni").associateWith { UnicodeScript.BENGALI } +
        listOf("ur", "fa", "ps", "sd", "ckb", "ug").associateWith { UnicodeScript.ARABIC } +
        listOf("uk", "bg", "sr", "mk", "be", "kk", "ky", "mn", "tg").associateWith { UnicodeScript.CYRILLIC } +
        listOf("ja", "yue").associateWith { UnicodeScript.HAN } +
        mapOf("yi" to UnicodeScript.HEBREW, "el" to UnicodeScript.GREEK)

    /** [tag]'s primary language, lower case and in today's code ("iw-IL" → "he"); null when blank. */
    fun canonical(tag: String?): String? {
        val primary = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
        return primary?.takeIf { it.isNotEmpty() }?.let { LEGACY[it] ?: it }
    }

    /** True for "en", "hi" and "bn": the languages with Audora's own voices, not the phone's. */
    fun isTuned(tag: String?): Boolean =
        canonical(tag)?.let { primary -> Lang.entries.any { it.bcp47Tag == primary } } == true

    /** The phone language [tag] names, or null for none, blank, or one of the three ([isTuned]). */
    fun phoneLanguage(tag: String?): String? = canonical(tag)?.takeUnless { isTuned(it) }

    /** The script [tag] is written in; Latin for a language the tables do not name. Kana count as Han. */
    fun scriptOf(tag: String): UnicodeScript = WRITTEN_IN[canonical(tag)] ?: UnicodeScript.LATIN

    /**
     * The phone language a letter of [script] is read in by default; null for Latin, Devanagari and Bengali (read
     * by [Lang]'s voices), for script-neutral characters, and for scripts the table does not name.
     */
    fun defaultFor(script: UnicodeScript): String? = DEFAULTS[unified(script)]

    /** Hiragana and katakana are read with the Han letters around them, as one Japanese run. */
    fun unified(script: UnicodeScript): UnicodeScript = when (script) {
        UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA -> UnicodeScript.HAN
        else -> script
    }

    /** Whether [script] is kana, which makes the Han letters of its sentence Japanese. */
    fun isKana(script: UnicodeScript): Boolean = script == UnicodeScript.HIRAGANA || script == UnicodeScript.KATAKANA
}
