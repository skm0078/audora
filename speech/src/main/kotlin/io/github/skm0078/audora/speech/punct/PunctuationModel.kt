// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/PunctuationModel.kt
// Purpose: The pinned punctuation model download (RI-11): its zip on the voice mirror and the two files inside it.

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.speech.voices.PinnedFile

/**
 * A zip on the app's own voice mirror (ADR-022), built by scripts/build_voice_mirror.py from speech/models.json's
 * `models` entry. The pins are repeated here so the app needs no catalog parser for one file; a JVM test checks they
 * match models.json, so the two cannot drift.
 *
 * @property modelFile and [vocabFile] are the zip entries sherpa-onnx's CNN-BiLSTM punctuation model needs.
 */
data class PunctuationModel(
    override val id: String,
    val fileName: String,
    override val sha256: String,
    override val sizeBytes: Long,
    val modelFile: String,
    val vocabFile: String,
    val baseUrl: String,
) : PinnedFile {
    override val url: String get() = baseUrl + fileName

    companion object {
        /** Edge-Punct-Casing's English CNN-BiLSTM, int8 (Apache-2.0); adds commas, full stops and question marks. */
        val ENGLISH = PunctuationModel(
            id = "punct-en-cnn-bilstm",
            fileName = "punct-en-cnn-bilstm-a8fac296fa09.zip",
            sha256 = "a8fac296fa09911b974292efc845e028cd38d99809dad0186529d3aa4eb6e332",
            sizeBytes = 7_640_490,
            modelFile = "model.int8.onnx",
            vocabFile = "bpe.vocab",
            baseUrl = "https://github.com/skm0078/audora/releases/download/voices/",
        )
    }
}
