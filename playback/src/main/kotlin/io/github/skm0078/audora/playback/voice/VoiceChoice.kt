// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/VoiceChoice.kt
// Purpose: Which chosen voice reads a language (book's while installed, session's, Settings'), and a run's fixed copy.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId

/**
 * First match wins: the open book's own voice (plan item 4) only while [canSpeak] says it is still installed, then
 * Settings → Voices (which the reader's voice picker also saves to). Null means the language reads with its default.
 * Playback and the studio's audio keys both resolve through this, so a deleted book voice is neither heard nor named
 * in a key.
 */
internal object VoiceChoice {
    fun of(book: VoiceId?, canSpeak: (VoiceId) -> Boolean, settings: () -> VoiceId?): VoiceId? =
        book?.takeIf(canSpeak) ?: settings()
}

/**
 * A whole-book run's choices, fixed once: every language's [VoiceChoice] read at the start and kept, so the run neither
 * sees a voice picked in Settings meanwhile nor binds the phone's TTS to find out (a language left to its default
 * still resolves on its first sentence). Unfixed, [choiceFor] is [live]. Thread-safe.
 */
internal class FixedChoices {
    @Volatile private var fixed: Map<Lang, VoiceId?>? = null

    /** Reads [live] for every language on the first call and answers with that from now on; returns what was fixed. */
    @Synchronized
    fun fix(live: (Lang) -> VoiceId?): Map<Lang, VoiceId?> =
        fixed ?: Lang.entries.associateWith(live).also { fixed = it }

    fun choiceFor(lang: Lang, live: (Lang) -> VoiceId?): VoiceId? {
        val run = fixed
        return if (run != null) run[lang] else live(lang)
    }
}
