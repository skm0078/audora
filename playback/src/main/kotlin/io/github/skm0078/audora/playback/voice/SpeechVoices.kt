// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/SpeechVoices.kt
// Purpose: The playback service's speech engines and which voice reads each language.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.config.VoiceShapingConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.BundledSpeech
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechEngines
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.sherpa.VitsNoise
import io.github.skm0078.audora.speech.system.SystemSpeechEngine
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import io.github.skm0078.audora.speech.voices.VoiceSpeeds
import java.util.concurrent.ConcurrentHashMap

/**
 * Owned by the playback service and released with it, so the neural models (~60 MB each, ~150 MB for a premium
 * voice) never outlive playback. Engines are created lazily: the bundled model loads on its first render, the phone's
 * TTS binds on first use.
 *
 * Which voice reads a language, first match wins: the open book's own voice ([bookVoice], plan item 4),
 * [select] (the SET_VOICE command, for this service's life), the
 * voice picked in Settings → Voices (P4-01; read per sentence, so a change applies from the next sentence rendered),
 * then the default (Build Spec §3): English = the bundled neural voice; Hindi and Bengali = the phone's best offline
 * voice for the language. Thread-safe: the render worker resolves voices while the main thread may change them.
 */
class SpeechVoices(private val engines: VoiceEngines) : VoiceSource {
    private val chosen = ConcurrentHashMap<Lang, VoiceId>()
    private val defaults = ConcurrentHashMap<Lang, VoiceId>()

    /** The open book's own voice for a language (plan item 4), ahead of every other choice; null = none. */
    @Volatile
    var bookVoice: (Lang) -> VoiceId? = { null }

    fun select(lang: Lang, voice: VoiceId) {
        chosen[lang] = voice
        AudoraLog.i(TAG, "Voice for ${lang.name} set to $voice")
    }

    /**
     * The voice that reads [lang] now if it is already known, without binding the phone's TTS: what the render-ahead
     * window sizes for. Null before the language's default was first resolved.
     */
    fun knownVoice(lang: Lang): VoiceId? =
        bookVoice(lang)?.takeIf(engines::canSpeak) ?: chosen[lang] ?: engines.preferred(lang) ?: defaults[lang]

    /** How fast the voice reading [lang] renders on this phone; null if unknown or not measured yet (P4-02). */
    fun speedOf(lang: Lang): VoiceSpeed? = knownVoice(lang)?.let { engines.speeds().get(it) }

    /** The voice reading [lang]. Suspends while the phone's TTS binds to find a default. */
    override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> {
        val known = knownVoice(lang)
        if (known != null) return Outcome.Success(known)
        val resolved = if (lang == Lang.EN) engines.bundled().map { it.voice.id } else engines.system().bestVoice(lang)
        if (resolved is Outcome.Success) defaults.putIfAbsent(lang, resolved.value)
        return resolved
    }

    override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = when (id) {
        EngineId.SHERPA -> engines.bundled().map { it.engine }
        EngineId.SYSTEM -> Outcome.Success(engines.system())
    }

    /** Android asked for memory back: keep only the neural model in use (P4-02). Main thread. */
    fun trimMemory() = engines.trimMemory()

    fun release() = engines.release()

    private companion object {
        const val TAG = "Playback.Voices"
    }
}

/**
 * Creates each engine once, on first use, and releases only what was created. [expressiveness] is the listener's step
 * (plan item 8), read on every neural render; the Piper and MMS voices reload when it changes.
 */
class VoiceEngines(
    context: Context,
    config: SpeechConfig = SpeechConfig(),
    shapingConfig: VoiceShapingConfig = VoiceShapingConfig(),
    expressiveness: () -> Int = { VoiceShaping.DEFAULT_EXPRESSION },
) {
    private val appContext = context.applicationContext
    private val bundled = lazy {
        SpeechEngines.bundled(appContext, config) {
            val (scale, width) = shapingConfig.expressionNoise(expressiveness())
            VitsNoise(scale, width)
        }
    }
    private val system = lazy { SpeechEngines.system(appContext, config) }
    private val library = lazy { VoiceLibrary.get(appContext) }

    /** The voice picked for [lang] in Settings → Voices, if it is still installed. */
    fun preferred(lang: Lang): VoiceId? = library.value.preferredVoice(lang)

    /** Whether [voice] is still on the phone; a book's own voice that was deleted falls back to the reading voice. */
    fun canSpeak(voice: VoiceId): Boolean = library.value.canSpeak(voice)

    fun bundled(): Outcome<BundledSpeech, SpeechError> = bundled.value

    /** Measured voice speeds, shared with the Voices screen. */
    fun speeds(): VoiceSpeeds = library.value.speeds

    fun system(): SystemSpeechEngine = system.value

    /** Drops all but the most recently used neural model, if the neural engine was ever created; never creates it. */
    fun trimMemory() {
        if (bundled.isInitialized()) bundled.value.getOrNull()?.engine?.trimMemory()
    }

    fun release() {
        if (library.isInitialized()) library.value.speeds.save()
        if (bundled.isInitialized()) bundled.value.getOrNull()?.engine?.release()
        if (system.isInitialized()) system.value.release()
    }
}
