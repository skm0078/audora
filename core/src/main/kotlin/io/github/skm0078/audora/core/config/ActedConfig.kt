// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ActedConfig.kt
// Purpose: Tunables for the acted Hindi voice (Chatterbox) that studio mode renders with (ADR-059).

package io.github.skm0078.audora.core.config

/**
 * @property threads ONNX Runtime threads; four matches the Moto's big cores, as for the SYSPIN voices (ADR-053).
 * @property plainStrength Chatterbox's "exaggeration" for a sentence that asks for no feeling: its own default.
 * @property feelingStrength for a sentence that asks for one (ADR-056). Kept well under 0.9, where the PyTorch spike
 *   heard the model run on into made-up words.
 * @property retryStrength the second try after a runaway or cut-short take: plainer, which steadies the model.
 * @property repetitionPenalty as the export's reference script decodes; [retryPenalty] for the second try.
 * @property maxCharsPerSecond a take shorter than this reading speed lost words (stopped early): try again.
 * @property capCharsPerSecond and [capExtraSeconds] bound the token loop: a slow, acted reading of the text plus a
 *   breath, and no more, so a runaway stops within seconds instead of babbling to the model's limit.
 * @property maxPieceChars longer text is read in pieces split at clause marks, each within the model's reach.
 * @property pieceGapMs silence between such pieces, like a comma's pause.
 */
data class ActedConfig(
    val threads: Int = 4,
    val plainStrength: Float = 0.5f,
    val feelingStrength: Float = 0.65f,
    val retryStrength: Float = 0.4f,
    val repetitionPenalty: Float = 1.2f,
    val retryPenalty: Float = 1.35f,
    val maxCharsPerSecond: Float = 25f,
    val capCharsPerSecond: Float = 8f,
    val capExtraSeconds: Float = 1.5f,
    val maxPieceChars: Int = 220,
    val pieceGapMs: Int = 180,
) {
    init {
        require(threads >= 1) { "threads must be >= 1, was $threads" }
        require(plainStrength in STRENGTHS) { "plainStrength must be in $STRENGTHS, was $plainStrength" }
        require(feelingStrength in STRENGTHS) { "feelingStrength must be in $STRENGTHS, was $feelingStrength" }
        require(retryStrength in STRENGTHS) { "retryStrength must be in $STRENGTHS, was $retryStrength" }
        require(repetitionPenalty >= 1f) { "repetitionPenalty must be >= 1, was $repetitionPenalty" }
        require(retryPenalty >= 1f) { "retryPenalty must be >= 1, was $retryPenalty" }
        require(capCharsPerSecond > 0f && capCharsPerSecond < maxCharsPerSecond) {
            "capCharsPerSecond must be in (0, maxCharsPerSecond), was $capCharsPerSecond"
        }
        require(capExtraSeconds >= 0f) { "capExtraSeconds must be >= 0, was $capExtraSeconds" }
        require(maxPieceChars >= MIN_PIECE) { "maxPieceChars must be >= $MIN_PIECE, was $maxPieceChars" }
        require(pieceGapMs >= 0) { "pieceGapMs must be >= 0, was $pieceGapMs" }
    }

    private companion object {
        val STRENGTHS = 0f..2f
        const val MIN_PIECE = 40
    }
}
