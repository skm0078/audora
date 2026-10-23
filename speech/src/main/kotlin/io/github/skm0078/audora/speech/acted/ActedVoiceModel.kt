// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ActedVoiceModel.kt
// Purpose: The pinned acted Hindi voice downloads (ADR-059): its two zips on the voice mirror and their files.

package io.github.skm0078.audora.speech.acted

import io.github.skm0078.audora.speech.voices.ZipModel

/**
 * A zip on the app's own voice mirror (ADR-022), built by scripts/build_voice_mirror.py from speech/models.json's
 * `models` entry, as the punctuation model is. The pins are repeated here so the app needs no catalog parser for one
 * file; ActedVoiceModelTest checks they match models.json, so the two cannot drift.
 */
data class ActedVoiceModel(
    override val id: String,
    val fileName: String,
    override val sha256: String,
    override val sizeBytes: Long,
    override val files: List<String>,
    val baseUrl: String,
) : ZipModel {
    override val url: String get() = baseUrl + fileName

    companion object {
        private const val MIRROR = "https://github.com/skm0078/audora/releases/download/voices/"

        /**
         * Chatterbox Multilingual (Resemble AI, MIT), ONNX export by onnx-community: the text embedder, Chatterbox
         * Turbo's one-step decoder and the reference voice encoded for both.
         */
        val CORE = ActedVoiceModel(
            id = "acted-hi-chatterbox-core",
            fileName = "acted-hi-chatterbox-core-7987e6a33727.zip",
            sha256 = "7987e6a3372764e0ac1fcb475fce29d6e12807727bddf27e1f4f3a867adb5a37",
            sizeBytes = 839_162_865L,
            files = listOf(
                "embed_tokens.onnx",
                "embed_tokens.onnx_data",
                "conditional_decoder.onnx",
                "conditional_decoder.onnx_data",
                "voice.bin",
            ),
            baseUrl = MIRROR,
        )

        /** Its language model at full precision, on its own: over GitHub's 2 GiB file limit together with [CORE]. */
        val LANGUAGE_MODEL = ActedVoiceModel(
            id = "acted-hi-chatterbox-lm",
            fileName = "acted-hi-chatterbox-lm-17c5351e0071.zip",
            sha256 = "17c5351e0071f3c27d695203144636a1a58b5607ab735f8caae7635d97b93583",
            sizeBytes = 2_080_822_670L,
            files = listOf("language_model.onnx", "language_model.onnx_data"),
            baseUrl = MIRROR,
        )

        /** Both parts, in download order. */
        val PARTS = listOf(CORE, LANGUAGE_MODEL)
    }
}
