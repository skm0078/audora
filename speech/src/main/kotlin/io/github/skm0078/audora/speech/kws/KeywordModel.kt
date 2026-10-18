// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/KeywordModel.kt
// Purpose: The pinned voice-command model download (plan item 38): its zip on the voice mirror and its files.

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.speech.voices.ZipModel

/**
 * A zip on the app's own voice mirror (ADR-022), built by scripts/build_voice_mirror.py from speech/models.json's
 * `models` entry, like the punctuation model; a JVM test checks the pins here match it.
 */
data class KeywordModel(
    override val id: String,
    val fileName: String,
    override val sha256: String,
    override val sizeBytes: Long,
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
    val baseUrl: String,
) : ZipModel {
    override val url: String get() = baseUrl + fileName
    override val files: List<String> get() = listOf(encoder, decoder, joiner, tokens)

    companion object {
        /** k2-fsa's 3.3M-parameter zipformer keyword spotter trained on GigaSpeech (Apache-2.0), int8. */
        val ENGLISH = KeywordModel(
            id = "kws-en-zipformer-gigaspeech",
            fileName = "kws-en-zipformer-gigaspeech-325100325fc0.zip",
            sha256 = "325100325fc0b984169e2654d3b4fa01194df28bf8182a0b98d0b1d3f5c68dfa",
            sizeBytes = 5_254_988,
            encoder = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            decoder = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            joiner = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            tokens = "tokens.txt",
            baseUrl = "https://github.com/skm0078/audora/releases/download/voices/",
        )
    }
}
