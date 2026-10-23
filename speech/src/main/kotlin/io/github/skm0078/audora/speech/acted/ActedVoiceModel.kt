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
            fileName = "acted-hi-chatterbox-core-PENDING.zip",
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            sizeBytes = 1L,
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
            fileName = "acted-hi-chatterbox-lm-PENDING.zip",
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            sizeBytes = 1L,
            files = listOf("language_model.onnx", "language_model.onnx_data"),
            baseUrl = MIRROR,
        )

        /** Both parts, in download order. */
        val PARTS = listOf(CORE, LANGUAGE_MODEL)
    }
}
