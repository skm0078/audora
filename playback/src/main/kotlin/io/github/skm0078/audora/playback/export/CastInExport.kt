// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/CastInExport.kt
// Purpose: Whether an export can read characters in voices of their own (plan item 63), so it never promises a cast.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.model.Lang

/**
 * Character voices are the other speakers of the narrator's own model, and only built-in English voices whose speaker
 * table is known have them (Kokoro and Kitten). An imported folder of the same model, any one-speaker voice and every
 * Hindi and Bengali voice read all characters in one voice; the export says which instead of offering a cast it
 * cannot apply.
 */
enum class CastInExport {
    /** "A voice for each character" is off, or nothing has been read yet. */
    OFF,

    /** Every language read has a cast: characters get voices of their own, the ones playback gave them. */
    CAST,

    /** The English narrator has no other speakers (whatever else the book reads). */
    NEEDS_BUILT_IN_VOICE,

    /** English characters get voices, but the book also reads Hindi or Bengali, which have none. */
    ENGLISH_ONLY,
    ;

    companion object {
        /**
         * @param languages the languages the export reads (collected as pages are read).
         * @param castSize how many other speakers the narrator of a language has; zero when no table is known.
         */
        fun of(characterVoices: Boolean, languages: Set<Lang>, castSize: (Lang) -> Int): CastInExport {
            if (!characterVoices || languages.isEmpty()) return OFF
            val uncast = languages.filter { castSize(it) <= 0 }
            return when {
                uncast.isEmpty() -> CAST
                Lang.EN in uncast -> NEEDS_BUILT_IN_VOICE
                else -> ENGLISH_ONLY
            }
        }
    }
}
