// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/listen/BlindOrder.kt
// Purpose: Blind listening test: each language's voices get letters in a shuffled, stable order, so the tester
//          rates "Voice C" without knowing it is Kokoro. The seed is stored, so the letters survive restarts.

package io.github.skm0078.audora.spike.voices.listen

import kotlin.random.Random

data class BlindEntry(val letter: String, val voiceId: String)

object BlindOrder {
    fun assign(voiceIds: List<String>, seed: Long): List<BlindEntry> =
        voiceIds.sorted().shuffled(Random(seed)).mapIndexed { index, id -> BlindEntry(letter(index), id) }

    /** A, B, … Z, then AA, AB, … (more voices than letters never happens here, but must not crash). */
    fun letter(index: Int): String {
        require(index >= 0) { "index must be >= 0" }
        val alphabet = 'Z' - 'A' + 1
        return if (index < alphabet) "${'A' + index}" else letter(index / alphabet - 1) + letter(index % alphabet)
    }
}
