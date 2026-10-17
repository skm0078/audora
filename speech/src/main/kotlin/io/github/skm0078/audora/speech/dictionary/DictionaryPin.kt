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
            fileName = "dict-en-oewn-2025-ea38f365dd95.zip",
            sha256 = "ea38f365dd95bcc582fc2a693822f93fbe45addfe50d5a0dde5bc79d99d343b7",
            sizeBytes = 5_364_971,
            unpackedBytes = 16_202_235,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = MIRROR,
        )

        /** Hindi entries of Wiktionary (CC BY-SA 4.0, via kaikki.org): English glosses and inflected forms. */
        val HINDI = DictionaryPin(
            id = "dict-hi-wiktionary",
            fileName = "dict-hi-wiktionary-c2f5e6ff1696.zip",
            sha256 = "c2f5e6ff1696fe4b24cb37df7127387ff51a96cd0ab3cbd9b38612730352a904",
            sizeBytes = 1_822_960,
            unpackedBytes = 15_563_759,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = MIRROR,
        )

        /** Bengali entries of Wiktionary (CC BY-SA 4.0, via kaikki.org): English glosses and inflected forms. */
        val BENGALI = DictionaryPin(
            id = "dict-bn-wiktionary",
            fileName = "dict-bn-wiktionary-7d37e76f7fd3.zip",
            sha256 = "7d37e76f7fd3a966d55a8458b0e58c9f21b0b194e87fc0234e1e5743d313788d",
            sizeBytes = 567_256,
            unpackedBytes = 3_341_276,
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
