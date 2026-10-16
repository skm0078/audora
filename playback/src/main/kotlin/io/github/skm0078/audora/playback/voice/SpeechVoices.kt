// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/SpeechVoices.kt
// Purpose: The playback service's speech engines and which voice reads each language.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.BundledSpeech
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechEngines
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.system.SystemSpeechEngine
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import java.util.concurrent.ConcurrentHashMap

/**
 * Owned by the playback service and released with it, so the ~60 MB neural model never outlives playback.
 * Engines are created lazily: the bundled model loads on its first render, the phone's TTS binds on first use.
 *
 * Which voice reads a language, first match wins: [select] (the SET_VOICE command, for this service's life), the
 * voice picked in Settings → Voices (P4-01; read per sentence, so a change applies from the next sentence rendered),
 * then the default (Build Spec §3): English = the bundled neural voice; Hindi and Bengali = the phone's best offline
 * voice for the language. Thread-safe: the render worker resolves voices while the main thread may change them.
 */
class SpeechVoices(private val engines: VoiceEngines) {
    private val chosen = ConcurrentHashMap<Lang, VoiceId>()
    private val defaults = ConcurrentHashMap<Lang, VoiceId>()

    fun select(lang: Lang, voice: VoiceId) {
        chosen[lang] = voice
        AudoraLog.i(TAG, "Voice for ${lang.name} set to $voice")
    }

    /** The voice reading [lang]. Suspends while the phone's TTS binds to find a default. */
    suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> {
        val known = chosen[lang] ?: engines.preferred(lang) ?: defaults[lang]
        if (known != null) return Outcome.Success(known)
        val resolved = if (lang == Lang.EN) engines.bundled().map { it.voice.id } else engines.system().bestVoice(lang)
        if (resolved is Outcome.Success) defaults.putIfAbsent(lang, resolved.value)
        return resolved
    }

    fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = when (id) {
        EngineId.SHERPA -> engines.bundled().map { it.engine }
        EngineId.SYSTEM -> Outcome.Success(engines.system())
    }

    fun release() = engines.release()

    private companion object {
        const val TAG = "Playback.Voices"
    }
}

/** Creates each engine once, on first use, and releases only what was created. */
class VoiceEngines(context: Context, config: SpeechConfig = SpeechConfig()) {
    private val appContext = context.applicationContext
    private val bundled = lazy { SpeechEngines.bundled(appContext, config) }
    private val system = lazy { SpeechEngines.system(appContext, config) }
    private val library = lazy { VoiceLibrary.get(appContext) }

    /** The voice picked for [lang] in Settings → Voices, if it is still installed. */
    fun preferred(lang: Lang): VoiceId? = library.value.preferredVoice(lang)

    fun bundled(): Outcome<BundledSpeech, SpeechError> = bundled.value

    fun system(): SystemSpeechEngine = system.value

    fun release() {
        if (bundled.isInitialized()) bundled.value.getOrNull()?.engine?.release()
        if (system.isInitialized()) system.value.release()
    }
}
