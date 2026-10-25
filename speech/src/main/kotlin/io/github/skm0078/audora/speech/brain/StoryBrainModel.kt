// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainModel.kt
// Purpose: The pinned story brain model (ADR-078): one GGUF file, downloaded as parts from the app's own release.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.speech.voices.PinnedFile

/**
 * Built by scripts/build_story_brain_mirror.py from speech/story-brain.json. The pins are repeated here so the app
 * needs no catalog parser for one model; a JVM test checks they match the JSON, so the two cannot drift.
 *
 * @property sha256 and [sizeBytes] are the joined file's; each of [parts] carries its own.
 */
data class StoryBrainModel(
    val id: String,
    val sha256: String,
    val sizeBytes: Long,
    val parts: List<Part>,
    val licence: String,
) {
    /** One byte range of the file, a download of its own. */
    data class Part(
        override val id: String,
        override val url: String,
        override val sha256: String,
        override val sizeBytes: Long,
    ) : PinnedFile

    companion object {
        private const val BASE_URL = "https://github.com/skm0078/audora/releases/download/story-brain/"
        private const val QWEN = "brain-qwen35-4b-q4km"
        private const val PART_PREFIX = "$QWEN-00fe7986ff5f.part"

        /** Qwen3.5 4B by the Qwen team (Apache-2.0), Unsloth's Q4_K_M GGUF, unchanged. */
        val QWEN35_4B = StoryBrainModel(
            id = QWEN,
            sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
            sizeBytes = 2_740_937_888,
            parts = listOf(
                Part(
                    "${PART_PREFIX}1",
                    "$BASE_URL${PART_PREFIX}1",
                    "416e11f3cf0391279546e73fd7a3658c368ae4328f0a94d34b6d7d6d0394964f",
                    1_500_000_000,
                ),
                Part(
                    "${PART_PREFIX}2",
                    "$BASE_URL${PART_PREFIX}2",
                    "d4761367a069f853868471b7c11cd9a65c6112e5d9476b9c3ac2bd32bcebacfb",
                    1_240_937_888,
                ),
            ),
            licence = "Apache-2.0",
        )
    }
}
