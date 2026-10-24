// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/SpeechStyleChange.kt
// Purpose: One change to the speech style, the fields left null kept as the service has them saved.

package io.github.skm0078.audora.playback.contract

import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceShaping

/** What SET_SPEECH_STYLE changes; null fields stay as saved. */
data class SpeechStyleChange(
    val rate: Float? = null,
    val pace: Pace? = null,
    val clausePauses: Boolean? = null,
    val modes: ReadingModes? = null,
    val shaping: VoiceShaping? = null,
    val pitch: Float? = null,
) {
    /** [base] with this change's fields on top. */
    fun over(base: SpeechStyle): SpeechStyle = SpeechStyle(
        rate = rate ?: base.rate,
        pace = pace ?: base.pace,
        clausePauses = clausePauses ?: base.clausePauses,
        modes = modes ?: base.modes,
        shaping = shaping ?: base.shaping,
        pitch = pitch ?: base.pitch,
    )
}
