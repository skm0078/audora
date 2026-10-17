// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryPin.kt
// Purpose: The pinned dictionary download (plan item 33, ADR-045): its zip on the voice mirror and the file inside.

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.speech.voices.PinnedFile

/**
 * A zip on the app's own voice mirror (ADR-022), built by scripts/build_dictionary.py from speech/models.json's
 * `dictionaries` entry. The pins are repeated here so the app needs no catalog parser for one file; a JVM test checks
 * they match models.json.
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
        /** Open English Wordnet 2025 (CC BY 4.0, from Princeton WordNet): definitions and synonyms. */
        val ENGLISH = DictionaryPin(
            id = "dict-en-oewn-2025",
            fileName = "dict-en-oewn-2025-4b43c1a566a4.zip",
            sha256 = "4b43c1a566a4562b21317584d40c0a035f16a27e960555343067087242feba99",
            sizeBytes = 5_364_365,
            unpackedBytes = 16_202_235,
            entryName = "dictionary.tsv",
            licenceName = "LICENSE",
            baseUrl = "https://github.com/skm0078/audora/releases/download/voices/",
        )
    }
}
