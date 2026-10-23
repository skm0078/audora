// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ActedVoiceModel.kt
// Purpose: The pinned acted Hindi voice download (ADR-059): its zip on the voice mirror and the files inside it.

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
        /** Chatterbox Multilingual (Resemble AI, MIT), ONNX export by onnx-community, with its reference voice. */
        val HINDI = ActedVoiceModel(
            id = "acted-hi-chatterbox",
            fileName = "acted-hi-chatterbox-PENDING.zip",
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            sizeBytes = 1L,
            files = listOf(
                "embed_tokens.onnx",
                "embed_tokens.onnx_data",
                "language_model.onnx",
                "language_model.onnx_data",
                "conditional_decoder.onnx",
                "conditional_decoder.onnx_data",
                "voice.bin",
            ),
            baseUrl = "https://github.com/skm0078/audora/releases/download/voices/",
        )
    }
}
