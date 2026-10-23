// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryPin.kt
// Purpose: The pinned dictionary downloads (item 33, ADR-045, ADR-047): each zip on the voice mirror and its file.

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.PinnedFile

/**
 * A zip on the app's own voice mirror (ADR-022), built by scripts/build_dictionary.py from speech/models.json's
 * `dictionaries` entries. The pins are repeated here so the app needs no catalog parser; a JVM test checks they match
 * models.json.
 *
 * @property entryName the sorted dictionary text inside the zip; [licenceName] its licence, kept beside it.
 * @property unpackedBytes the text's size once unpacked, for the space check and the size shown before downloading.
 */
data class DictionaryPin(
    override val id: String,
    val fileName: String,
    override val sha256: String,
    override val sizeBytes: Long,
    val unpackedBytes: Long,
    val entryName: String,
    val licenceName: String,
    val baseUrl: String,
) : PinnedFile {
    override val url: String get() = baseUrl + fileName

    companion object {
        private const val MIRROR = "https://github.com/skm0078/audora/releases/download/voices/"

        /** Open English Wordnet 2025 (CC BY 4.0, from Princeton WordNet): definitions and synonyms. */
        val ENGLISH = DictionaryPin(
            id = "dict-en-oewn-2025",
            fileName = "dict-en-oewn-2025-12568d7b873e.zip",
            sha256 = "12568d7b873ed3af24f00eb6b04bb14bedfa0680253c186a62aec7e5a522d8bd",
            sizeBytes = 5_352_460,
            unpackedBytes = 16_206_552,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = MIRROR,
        )

        /** Hindi entries of Wiktionary (CC BY-SA 4.0, via kaikki.org): English glosses and inflected forms. */
        val HINDI = DictionaryPin(
            id = "dict-hi-wiktionary",
            fileName = "dict-hi-wiktionary-b2bc32c39cfd.zip",
            sha256 = "b2bc32c39cfdca09acb81c0468e04022eafe084a554f3699581b7e9e8946f469",
            sizeBytes = 1_792_646,
            unpackedBytes = 15_563_758,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = MIRROR,
        )

        /** Bengali entries of Wiktionary (CC BY-SA 4.0, via kaikki.org): English glosses and inflected forms. */
        val BENGALI = DictionaryPin(
            id = "dict-bn-wiktionary",
            fileName = "dict-bn-wiktionary-a50babce19ad.zip",
            sha256 = "a50babce19ad1123f715b43199f2312117762d4aef63091bd6fe16c014e8019b",
            sizeBytes = 556_861,
            unpackedBytes = 3_341_270,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = MIRROR,
        )

        fun of(lang: Lang): DictionaryPin = when (lang) {
            Lang.EN -> ENGLISH
            Lang.HI -> HINDI
            Lang.BN -> BENGALI
        }
    }
}
