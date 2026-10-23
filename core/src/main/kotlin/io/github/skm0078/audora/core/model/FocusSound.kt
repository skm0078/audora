// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/FocusSound.kt
// Purpose: The focus sound under the voice (plan item 72): which one and how loud, saved by the playback service.

package io.github.skm0078.audora.core.model

/** Generated on the phone, so there are no sound files to license or download. */
enum class FocusSound { OFF, RAIN, CAFE, BROWN_NOISE }

/**
 * @property level how loud the sound is, 0..1 of its loudest; it dips under the voice while the voice speaks.
 */
data class FocusSoundOptions(val sound: FocusSound = FocusSound.OFF, val level: Float = DEFAULT_LEVEL) {
    init {
        require(level in 0f..1f) { "level must be in 0..1, was $level" }
    }

    val on: Boolean get() = sound != FocusSound.OFF && level > 0f

    /** One line, `key=value` pairs separated by `;`, like [SleepOptions.encode]; [decode] reads it back. */
    fun encode(): String = "$KEY_SOUND=${sound.name};$KEY_LEVEL=$level"

    companion object {
        const val DEFAULT_LEVEL = 0.5f
        private const val KEY_SOUND = "sound"
        private const val KEY_LEVEL = "level"

        /** Reads [encode]'s line; a missing or bad value takes its default, so this never fails. */
        fun decode(line: String?): FocusSoundOptions {
            val values = line.orEmpty().split(';').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val sound = FocusSound.entries.firstOrNull { it.name == values[KEY_SOUND] } ?: FocusSound.OFF
            val level = values[KEY_LEVEL]?.toFloatOrNull()?.takeIf { it in 0f..1f } ?: DEFAULT_LEVEL
            return FocusSoundOptions(sound, level)
        }
    }
}
