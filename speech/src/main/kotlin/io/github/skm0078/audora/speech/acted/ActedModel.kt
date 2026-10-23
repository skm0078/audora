// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ActedModel.kt
// Purpose: The acted voice's model as the engine sees it, and the checks that catch a take gone wrong (ADR-059).

package io.github.skm0078.audora.speech.acted

import io.github.skm0078.audora.core.config.ActedConfig
import io.github.skm0078.audora.core.model.Feeling

/**
 * Chatterbox in two steps, as the native library runs it: [speak] turns text tokens into speech tokens, [decode]
 * turns those into audio. Blocking; one call at a time. [cancel] may come from any thread.
 */
interface ActedModel : AutoCloseable {
    /** Null when [cancel] stopped it. Throws [IllegalStateException] when ONNX Runtime fails. */
    fun speak(textTokens: LongArray, strength: Float, maxTokens: Int, repetitionPenalty: Float): ActedSpeech?

    /** Mono float audio at [SAMPLE_RATE]. Throws [IllegalStateException] on failure or cancel. */
    fun decode(speech: LongArray): FloatArray

    fun cancel()

    companion object {
        const val SAMPLE_RATE = 24_000
        const val TOKENS_PER_SECOND = 25
    }
}

/** @property stopped the model ended the speech itself; false = it hit the token cap (a runaway). */
class ActedSpeech(val tokens: LongArray, val stopped: Boolean)

/** One try at a piece of text: how strongly it is acted and how hard repeats are pushed down. */
data class ActedTake(val strength: Float, val repetitionPenalty: Float)

/**
 * The guard against Chatterbox's two failures: running on past the text into made-up words (heard at high
 * exaggeration in the spike), and stopping early with words lost. A take that fails either check is tried once more,
 * plainer; if that fails too the sentence is left to the live voice.
 */
class ActedTakes(private val config: ActedConfig = ActedConfig()) {
    /** The first try for a sentence asking for [feeling], then the steadier retry. */
    fun takesFor(feeling: Feeling): List<ActedTake> {
        val strength = if (feeling == Feeling.NEUTRAL) config.plainStrength else config.feelingStrength
        return listOf(
            ActedTake(strength, config.repetitionPenalty),
            ActedTake(minOf(strength, config.retryStrength), config.retryPenalty),
        )
    }

    /** Speech tokens a piece of [chars] characters may take: a slow reading of it plus a breath. */
    fun maxTokens(chars: Int): Int =
        ((chars / config.capCharsPerSecond + config.capExtraSeconds) * ActedModel.TOKENS_PER_SECOND).toInt()

    fun verdict(chars: Int, speech: ActedSpeech): Verdict {
        val seconds = speech.tokens.size.toFloat() / ActedModel.TOKENS_PER_SECOND
        return when {
            !speech.stopped -> Verdict.RAN_ON
            seconds * config.maxCharsPerSecond < chars -> Verdict.CUT_SHORT
            else -> Verdict.GOOD
        }
    }

    enum class Verdict { GOOD, RAN_ON, CUT_SHORT }
}

/**
 * Splits text longer than [ActedConfig.maxPieceChars] at the last clause mark (comma, semicolon, colon, dash) that
 * keeps the piece within it, else at the last space, so each piece stays within the model's reach. Shorter text is one
 * piece. Pieces are trimmed and never blank.
 */
object ActedPieces {
    private val clauseMarks = charArrayOf(',', ';', ':', '—', '–')

    fun of(text: String, maxChars: Int): List<String> {
        val pieces = ArrayList<String>()
        var rest = text.trim()
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            val clause = window.lastIndexOfAny(clauseMarks)
            val cut = when {
                clause > 0 -> clause + 1
                window.lastIndexOf(' ') > 0 -> window.lastIndexOf(' ')
                else -> maxChars
            }
            rest.substring(0, cut).trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces.add(rest)
        return pieces
    }
}
