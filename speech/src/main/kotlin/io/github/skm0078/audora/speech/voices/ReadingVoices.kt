// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/ReadingVoices.kt
// Purpose: The user's reading voice per language, cached in memory over VoicePreferences and safe across threads.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read by the playback service for every sentence (through [preferred]) and written by the Voices screen, so reads
 * come from memory; the file is read once, lazily.
 */
class ReadingVoices(private val preferences: VoicePreferences) {
    private val state = MutableStateFlow<Map<Lang, VoiceId>?>(null)
    private val lock = Any()

    /** Null until first read; then the user's choices. */
    val choices: StateFlow<Map<Lang, VoiceId>?> get() = state.asStateFlow()

    fun current(): Map<Lang, VoiceId> = state.value ?: synchronized(lock) {
        state.value ?: preferences.read().also { state.value = it }
    }

    /** The chosen voice for [lang] if [available] says it can still speak; else null ("use the default"). */
    fun preferred(lang: Lang, available: (VoiceId) -> Boolean): VoiceId? = current()[lang]?.takeIf(available)

    /** Makes [voice] read [lang]; null goes back to the default. Saved at once. */
    fun choose(lang: Lang, voice: VoiceId?) {
        synchronized(lock) {
            val next = current().toMutableMap().apply { if (voice == null) remove(lang) else put(lang, voice) }
            preferences.write(next)
            state.value = next
        }
        AudoraLog.i(TAG, "Reading voice for ${lang.name} set to ${voice ?: "default"}")
    }

    /**
     * Makes [voice] read [lang] only if the user has not picked one; true if it did. Used when the first voice of a
     * language finishes downloading (P3-06): the user downloaded it to hear it.
     */
    fun chooseIfUnset(lang: Lang, voice: VoiceId): Boolean = synchronized(lock) {
        if (current().containsKey(lang)) return false
        choose(lang, voice)
        true
    }

    /** A deleted voice stops being any language's choice. */
    fun forget(voice: VoiceId) {
        current().filterValues { it == voice }.keys.forEach { choose(it, null) }
    }

    private companion object {
        const val TAG = "Speech.ReadingVoices"
    }
}
