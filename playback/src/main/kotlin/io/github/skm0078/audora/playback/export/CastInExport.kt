// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/CastInExport.kt
// Purpose: Whether an export can read characters in voices of their own (plan item 63), so it never promises a cast.

package io.github.skm0078.audora.playback.export

/**
 * Character voices are the other speakers of the narrator's own model, and only built-in voices whose speaker table
 * is known have them (Kokoro and Kitten English). An imported folder of the same model, or any one-speaker voice,
 * reads every character in the dialogue voice; the export says so instead of offering a cast it cannot apply.
 */
enum class CastInExport {
    /** "A voice for each character" is off. */
    OFF,

    /** Characters get voices of their own, the same ones playback gave them in this book. */
    CAST,

    /** The setting is on but the narrator has no other speakers: characters keep the dialogue voice. */
    NARRATOR_ONLY,
    ;

    companion object {
        /** [castSpeakers]: how many other speakers the narrator's model offers (zero when no table is known). */
        fun of(characterVoices: Boolean, castSpeakers: Int): CastInExport = when {
            !characterVoices -> OFF
            castSpeakers <= 0 -> NARRATOR_ONLY
            else -> CAST
        }
    }
}
