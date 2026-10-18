// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/EnergyGate.kt
// Purpose: Skips decoding quiet microphone chunks, so screen-off listening costs little in a quiet room (item 38).

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig
import kotlin.math.pow

/**
 * The spotter's decode is the listener's main cost; the read itself is cheap. A chunk quieter than `gateDbfs` is held
 * back instead of decoded, the last `preRollChunks` of them kept, and the first loud chunk is decoded after them (a
 * phrase's soft start). Chunks keep being decoded for `hangoverMs` after the last loud one. Not thread-safe: one
 * listening loop owns it.
 */
internal class EnergyGate(config: VoiceCommandConfig) {
    private val thresholdMeanSquare = DB_BASE.pow(config.gateDbfs / DB_PER_DECADE_POWER)
    private val preRollChunks = config.preRollChunks
    private val hangoverChunks = config.hangoverChunks
    private val preRoll = ArrayDeque<FloatArray>()
    private var hangoverLeft = 0

    /** [chunk]: mono floats in -1..1, owned by the gate from now on. Returns the chunks to decode now, oldest first. */
    fun pass(chunk: FloatArray): List<FloatArray> {
        val loud = meanSquare(chunk) >= thresholdMeanSquare
        return when {
            loud -> (preRoll + chunk).also {
                preRoll.clear()
                hangoverLeft = hangoverChunks
            }

            hangoverLeft > 0 -> listOf(chunk).also { hangoverLeft-- }

            else -> emptyList<FloatArray>().also {
                preRoll.addLast(chunk)
                if (preRoll.size > preRollChunks) preRoll.removeFirst()
            }
        }
    }

    private fun meanSquare(chunk: FloatArray): Float =
        if (chunk.isEmpty()) 0f else chunk.fold(0f) { sum, s -> sum + s * s } / chunk.size

    private companion object {
        const val DB_BASE = 10f

        /** Power (mean square) decibels: 10 per decade. */
        const val DB_PER_DECADE_POWER = 10f
    }
}
