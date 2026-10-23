// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceNoteConfig.kt
// Purpose: Tunables for voice notes recorded on a sentence (plan item 32, ADR-041).

package io.github.skm0078.audora.core.config

/**
 * @property folder the recordings' folder under filesDir (private, part of backup).
 * @property bitRate AAC bit rate: 64 kbps mono is clear speech at about 0.5 MB a minute.
 * @property sampleRate recording sample rate in Hz.
 * @property maxDurationMs a recording stops by itself after this long (10 minutes).
 * @property minDurationMs a shorter recording is a slip of the finger: discarded, not saved.
 * @property orphanAgeMs a file with no note is deleted only once it is this old (an hour), so a recording in
 *   progress is safe.
 */
data class VoiceNoteConfig(
    val folder: String = "voice-notes",
    val bitRate: Int = 64_000,
    val sampleRate: Int = 44_100,
    val maxDurationMs: Int = 600_000,
    val minDurationMs: Long = 500L,
    val orphanAgeMs: Long = 3_600_000L,
) {
    init {
        require(folder.isNotBlank()) { "folder must not be blank" }
        require(bitRate > 0) { "bitRate must be > 0, was $bitRate" }
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        require(maxDurationMs > 0) { "maxDurationMs must be > 0, was $maxDurationMs" }
        require(minDurationMs >= 0) { "minDurationMs must be >= 0, was $minDurationMs" }
        require(orphanAgeMs >= 0) { "orphanAgeMs must be >= 0, was $orphanAgeMs" }
    }
}
