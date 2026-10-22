// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/KeywordTokens.kt
// Purpose: Checks every command phrase's pieces against the model's tokens.txt before the spotter loads (item 38).

package io.github.skm0078.audora.speech.kws

/**
 * sherpa-onnx 1.13.8 ends the whole process (`_Exit`) when a keywords file names a piece its model does not have,
 * so a typo in [VoiceCommand] or a different model would kill the app as the reader opens. The spotter is loaded
 * only once this finds nothing unknown.
 */
internal object KeywordTokens {
    /** The pieces in a sherpa-onnx `tokens.txt`: one "piece id" per line. */
    fun vocabulary(lines: Sequence<String>): Set<String> =
        lines.map { it.trim().substringBeforeLast(' ', missingDelimiterValue = "") }.filter { it.isNotEmpty() }.toSet()

    /** The pieces of [commands]' phrases that [vocabulary] lacks, in order, each once; empty when all are known. */
    fun unknown(vocabulary: Set<String>, commands: List<VoiceCommand> = VoiceCommand.entries): List<String> =
        commands.flatMap { command -> command.tokens.flatMap { it.split(' ') } }
            .filter { it.isNotEmpty() && it !in vocabulary }
            .distinct()
}
