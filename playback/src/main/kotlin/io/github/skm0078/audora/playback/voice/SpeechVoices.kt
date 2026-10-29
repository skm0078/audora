// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/SpeechVoices.kt
// Purpose: The playback service's speech engines and which voice reads each language.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.config.PhoneLanguageConfig
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
import io.github.skm0078.audora.speech.acted.ActedSpeechEngine
import io.github.skm0078.audora.speech.acted.ActedVoices
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.sherpa.NarratorBlend
import io.github.skm0078.audora.speech.sherpa.StyleBlend
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
 * Which voice reads a language, first match wins: the open book's own voice ([bookVoice], plan item 4), the
 * voice picked in Settings → Voices or by the reader's picker ([choose]; P4-01, read per sentence; the playback
 * service also restarts the sentence playing when it changes, so the sentences rendered ahead are redone), then the
 * default (Build Spec §3): English = the bundled neural voice; Hindi and Bengali = the phone's best offline
 * voice for the language. Thread-safe: the render worker resolves voices while the main thread may change them.
 */
@Suppress("TooManyFunctions") // The VoiceSource contract plus the queries the pickers and render-ahead make.
class SpeechVoices(
    private val engines: VoiceEngines,
    phoneConfig: PhoneLanguageConfig = PhoneLanguageConfig(),
    clock: () -> Long = System::currentTimeMillis,
) : VoiceSource {
    private val defaults = ConcurrentHashMap<Lang, VoiceId>()
    private val fixedChoices = FixedChoices()
    private val phoneVoices = PhoneVoices({ engines.system().bestVoiceFor(it) }, phoneConfig, clock)

    /** The open book's own voice for a language (plan item 4), ahead of every other choice; null = none. */
    @Volatile
    var bookVoice: (Lang) -> VoiceId? = { null }

    /**
     * Makes [voice] read [lang] in every book without voices of its own, saved as Settings → Voices' choice, so the
     * reader's picker and the Voices screen never disagree and the pick outlives this service.
     */
    fun choose(lang: Lang, voice: VoiceId) {
        engines.choose(lang, voice)
        AudoraLog.i(TAG, "Voice for ${lang.name} set to $voice")
    }

    /** Whether [voice] is still on the phone; a book's own voice that was deleted is read with the language's voice. */
    fun canSpeak(voice: VoiceId): Boolean = engines.canSpeak(voice)

    /** True when the open book reads [lang] with its own installed voice: a Settings → Voices pick does not apply. */
    fun bookOverrides(lang: Lang): Boolean = bookVoice(lang)?.let(engines::canSpeak) == true

    /**
     * The voice that reads [lang] now if it is already known, without binding the phone's TTS: what the render-ahead
     * window sizes for. Null before the language's default was first resolved.
     */
    fun knownVoice(lang: Lang): VoiceId? = choiceFor(lang) ?: defaults[lang]

    /**
     * The voice explicitly chosen for [lang] (the book's while still installed, or Settings → Voices),
     * null when the language reads with its default. Never binds the phone's TTS; studio mode keys its audio by it
     * (ADR-039), so a deleted book voice, which playback never reads with, is not named in a key either.
     */
    fun choiceFor(lang: Lang): VoiceId? = fixedChoices.choiceFor(lang) { live ->
        VoiceChoice.of(bookVoice(live), engines::canSpeak) { engines.preferred(live) }
    }

    /**
     * Fixes every language's [choiceFor] for the rest of this instance's life and returns it (a whole-book run's key).
     * For the voices a run builds for itself (studio, export, clip), never the playback service's: playback must keep
     * following the listener's picks. A language with no choice still binds its default voice on its first sentence.
     */
    fun fixChoices(): Map<Lang, VoiceId?> = fixedChoices.fix { lang -> choiceFor(lang) }

    /** How fast the voice reading [lang] renders on this phone; null if unknown or not measured yet (P4-02). */
    fun speedOf(lang: Lang): VoiceSpeed? = knownVoice(lang)?.let { engines.speeds().get(it) }

    /** The voice reading [lang]. Suspends while the phone's TTS binds to find a default. */
    override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> {
        val known = knownVoice(lang)
        if (known != null) return Outcome.Success(known)
        val resolved = if (lang == Lang.EN) engines.bundled().map { it.voice.id } else engines.system().bestVoice(lang)
        // Two calls racing on a cold start answer alike: the first voice stored wins.
        return if (resolved is Outcome.Success) {
            Outcome.Success(defaults.putIfAbsent(lang, resolved.value) ?: resolved.value)
        } else {
            resolved
        }
    }

    /** The phone's best offline voice for [language] (ADR 2026-10-26); suspends while the phone's TTS binds. */
    override suspend fun phoneVoiceFor(language: String): Outcome<VoiceId, SpeechError> = phoneVoices.voiceFor(language)

    override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = when (id) {
        EngineId.SHERPA -> engines.bundled().map { it.engine }
        EngineId.SYSTEM -> Outcome.Success(engines.system())
        EngineId.ACTED -> Outcome.Success(engines.acted())
    }

    /** Android asked for memory back: keep only the neural model in use (P4-02). Main thread. */
    fun trimMemory() = engines.trimMemory()

    fun release() = engines.release()

    private companion object {
        const val TAG = "Playback.Voices"
    }
}

/**
 * The phone's best offline voice for each phone language, as [find] gives it, found once and kept, as the default
 * voices are. A language with no voice answers from memory until [PhoneLanguageConfig.missingVoiceRetryMs] has
 * passed, then asks the phone again: a voice pack downloaded meanwhile is picked up without a restart.
 */
internal class PhoneVoices(
    private val find: suspend (String) -> Outcome<VoiceId, SpeechError>,
    private val config: PhoneLanguageConfig,
    private val clock: () -> Long,
) {
    private val found = ConcurrentHashMap<String, VoiceId>()
    private val missing = ConcurrentHashMap<String, Missing>()

    /** Why a phone language had no voice, and when that was found. */
    private class Missing(val error: SpeechError, val atMs: Long)

    suspend fun voiceFor(language: String): Outcome<VoiceId, SpeechError> {
        val now = clock()
        val known = found[language]
        val recent = missing[language]?.takeIf { now - it.atMs < config.missingVoiceRetryMs }
        return when {
            known != null -> Outcome.Success(known)
            recent != null -> Outcome.Failure(recent.error)
            else -> ask(language, now)
        }
    }

    private suspend fun ask(language: String, now: Long): Outcome<VoiceId, SpeechError> =
        when (val resolved = find(language)) {
            is Outcome.Success -> {
                missing.remove(language)
                // Two calls racing answer alike: the first voice stored wins.
                Outcome.Success(found.putIfAbsent(language, resolved.value) ?: resolved.value)
            }

            is Outcome.Failure -> resolved.also { missing[language] = Missing(it.error, now) }
        }
}

/**
 * Creates each engine once, on first use, and releases only what was created. [expressiveness] is the listener's step
 * (plan item 8), read on every neural render; the Piper and MMS voices reload when it changes. [blend] is the
 * listener's narrator blend (plan item 75) for the narrator it names, read the same way; that voice reloads when it
 * changes.
 */
class VoiceEngines(
    context: Context,
    config: SpeechConfig = SpeechConfig(),
    shapingConfig: VoiceShapingConfig = VoiceShapingConfig(),
    blend: () -> NarratorBlend? = { null },
    expressiveness: () -> Int = { VoiceShaping.DEFAULT_EXPRESSION },
) {
    private val appContext = context.applicationContext
    private val bundled = lazy {
        SpeechEngines.bundled(appContext, config, blend) {
            val (scale, width) = shapingConfig.expressionNoise(expressiveness())
            VitsNoise(scale, width)
        }
    }
    private val system = lazy { SpeechEngines.system(appContext, config) }
    private val acted = lazy { ActedVoices.get(appContext).engine() }
    private val library = lazy { VoiceLibrary.get(appContext) }

    /** The voice picked for [lang] in Settings → Voices, if it is still installed. */
    fun preferred(lang: Lang): VoiceId? = library.value.preferredVoice(lang)

    /** Saves [voice] as Settings → Voices' choice for [lang]. */
    fun choose(lang: Lang, voice: VoiceId) = library.value.readingVoices.choose(lang, voice)

    /** Whether [voice] is still on the phone; a book's own voice that was deleted falls back to the reading voice. */
    fun canSpeak(voice: VoiceId): Boolean = if (voice.engine == EngineId.ACTED) {
        voice == ActedSpeechEngine.HINDI && ActedVoices.get(appContext).isInstalled()
    } else {
        library.value.canSpeak(voice)
    }

    fun bundled(): Outcome<BundledSpeech, SpeechError> = bundled.value

    /** The neural engine if something already built it, else null: never builds it, so safe on the main thread. */
    fun bundledIfBuilt(): BundledSpeech? = if (bundled.isInitialized()) bundled.value.getOrNull() else null

    /** Measured voice speeds, shared with the Voices screen. */
    fun speeds(): VoiceSpeeds = library.value.speeds

    fun system(): SystemSpeechEngine = system.value

    /** The acted Hindi voice (ADR-059), for studio mode's own voices; loads its model on first render. */
    fun acted(): ActedSpeechEngine = acted.value

    /** Drops all but the most recently used neural model, if the neural engine was ever created; never creates it. */
    fun trimMemory() {
        if (bundled.isInitialized()) bundled.value.getOrNull()?.engine?.trimMemory()
    }

    fun release() {
        if (library.isInitialized()) library.value.speeds.save()
        if (bundled.isInitialized()) bundled.value.getOrNull()?.engine?.release()
        if (system.isInitialized()) system.value.release()
        if (acted.isInitialized()) acted.value.release()
    }
}

/** [shaping]'s narrator blend for the voice reading English now (plan item 75); null with no blend or no voice. */
fun SpeechVoices.narratorBlend(shaping: VoiceShaping): NarratorBlend? =
    shaping.styleBlend()?.let { blend -> knownVoice(Lang.EN)?.let { NarratorBlend(it, blend) } }

/** The shaping's narrator blend as the engine takes it (plan item 75); null when there is none. */
fun VoiceShaping.styleBlend(): StyleBlend? = blendWith?.let { StyleBlend(it, blendPercent / PERCENT) }

private const val PERCENT = 100f
