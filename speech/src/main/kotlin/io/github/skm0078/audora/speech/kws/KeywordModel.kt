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
        /**
         * k2-fsa's streaming Zipformer English recogniser trained on LibriSpeech only (Apache-2.0 weights, CC BY 4.0
         * data), int8, run as a keyword spotter. It replaced the 3.3M GigaSpeech spotter, whose training data is
         * licensed for non-commercial research only (ADR 2026-10-30-voice-commands-librispeech). The tutor's smaller
         * 20M LibriSpeech model cannot hear short single words, so it is not shared.
         */
        val ENGLISH = KeywordModel(
            id = "kws-en-zipformer-librispeech",
            fileName = "kws-en-zipformer-librispeech-e08608953a6b.zip",
            sha256 = "e08608953a6bda81af4666cb9269d7c2f942b86df69b2f730b6cd4be60fcfff1",
            sizeBytes = 70_914_922,
            encoder = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            decoder = "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            joiner = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            tokens = "tokens.txt",
            baseUrl = "https://github.com/skm0078/audora/releases/download/voices/",
        )
    }
}
