// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/SpeechEngines.kt
// Purpose: Builds the two engines (neural voices: bundled + downloaded, phone's TTS) from an Android Context.

package io.github.skm0078.audora.speech

import android.content.Context
import android.content.res.AssetManager
import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import io.github.skm0078.audora.speech.sherpa.AssetSherpaSynthesizerFactory
import io.github.skm0078.audora.speech.sherpa.BlendCache
import io.github.skm0078.audora.speech.sherpa.BlendReport
import io.github.skm0078.audora.speech.sherpa.FileSherpaSynthesizerFactory
import io.github.skm0078.audora.speech.sherpa.NarratorBlend
import io.github.skm0078.audora.speech.sherpa.RoutingSynthesizerFactory
import io.github.skm0078.audora.speech.sherpa.SherpaSpeechEngine
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceManifest
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import io.github.skm0078.audora.speech.sherpa.VitsNoise
import io.github.skm0078.audora.speech.sherpa.withNarratorBlend
import io.github.skm0078.audora.speech.sherpa.withNoise
import io.github.skm0078.audora.speech.system.AndroidSystemTtsClient
import io.github.skm0078.audora.speech.system.SystemSpeechEngine
import io.github.skm0078.audora.speech.voices.VoiceCredits
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The neural engine together with the voice the APK ships, which is the English default (ADR-012, ADR-014).
 * [blends]: whether the narrator blend (plan item 75) could be used, for the speech sheet.
 */
class BundledSpeech(val engine: SherpaSpeechEngine, val voice: VoiceInfo, val blends: BlendReport = BlendReport())

object SpeechEngines {
    /** Where the build puts the bundled voice inside the APK's assets (speech/build.gradle.kts). */
    const val BUNDLED_ASSET_DIR = "voices/bundled"

    private const val TAG = "Speech.Engines"
    private const val BUNDLED_DATA_DIR = "voices/bundled"
    private const val SYSTEM_WORK_DIR = "tts-system"
    private const val BLEND_DIR = "voice-blends"

    /**
     * The neural engine: the English voice packed into the APK plus every voice downloaded or imported in Settings →
     * Voices (P4-01), seen as they come and go. Cheap: reads one small properties file; a model loads on its first
     * render. Fails only when the APK was built without its voice, which is a build bug worth surfacing.
     *
     * [noise] is read on every render and given to the Piper and MMS (VITS) voices, `styled` for a model with
     * speaking styles (Rasa, [withNoise]), so an expressiveness change (plan item 8) reloads the voice on its next
     * sentence; the other families have no such values. [blend] is read the same way and given to the narrator's
     * Kokoro voice only (narrator blend, plan item 75).
     */
    fun bundled(
        context: Context,
        config: SpeechConfig = SpeechConfig(),
        blend: () -> NarratorBlend? = { null },
        noise: (styled: Boolean) -> VitsNoise? = { null },
    ): Outcome<BundledSpeech, SpeechError> {
        val assets = context.assets
        val spec = when (val read = readBundledSpec(assets)) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> {
                val error = SpeechError.VoiceUnavailable(
                    EngineId.SHERPA,
                    Reason.MODEL_FILES_MISSING,
                    detail = read.error,
                )
                AudoraLog.e(TAG, error.message)
                return Outcome.Failure(error)
            }
        }
        val fromAssets = AssetSherpaSynthesizerFactory(
            assets = assets,
            assetDir = BUNDLED_ASSET_DIR,
            dataRoot = File(context.noBackupFilesDir, BUNDLED_DATA_DIR),
            threads = config.sherpaThreads,
        )
        val library = VoiceLibrary.get(context)
        val blends = blendCache(context, config)
        val fromDisk = FileSherpaSynthesizerFactory(library::voiceDir, config.sherpaThreads, blends)
        // The bundled voice first: it wins if a downloaded voice ever shares its id (after a bundled-voice swap).
        val engine = SherpaSpeechEngine(
            source = { (listOf(spec) + library.installedSpecs()).withNoise(noise).withNarratorBlend(blend()) },
            factory = RoutingSynthesizerFactory(spec.id, fromAssets, fromDisk),
            maxLoadedVoices = config.maxLoadedVoices,
            bengaliPace = config.bengaliPace,
        )
        AudoraLog.i(TAG, "Bundled voice ${spec.voiceId} (${spec.family}, ${spec.lang})")
        return Outcome.Success(BundledSpeech(engine, spec.toVoiceInfo(), blends.report))
    }

    /** Where the narrator blends' copies live (plan item 75): a cache, rebuilt when missing. */
    fun blendCache(context: Context, config: SpeechConfig = SpeechConfig()): BlendCache =
        BlendCache(File(context.cacheDir, BLEND_DIR), config.blendHeadroomBytes)

    /** Bytes of the narrator blends' voices files (plan item 75), for the Storage screen. */
    suspend fun blendCacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
        blendCache(context).sizeBytes()
    }

    /**
     * Deletes the blends' voices files (logged). A blend in use is rebuilt the next time its voice loads, so this
     * frees the space for as long as no blend is chosen.
     */
    suspend fun clearBlendCache(context: Context): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        blendCache(context).clearAll()
    }

    /** The phone's own TTS engine. Connects lazily on first use. */
    fun system(context: Context, config: SpeechConfig = SpeechConfig()): SystemSpeechEngine =
        SystemSpeechEngine(AndroidSystemTtsClient(context, File(context.cacheDir, SYSTEM_WORK_DIR), config))

    /**
     * The languages the phone's own engine reads offline right now ([SystemSpeechEngine.offlineLanguages], ADR
     * 2026-10-26), for Edit details: binds the phone's TTS for this call only, then lets it go. Call on the main
     * thread or any other; it suspends while the engine binds.
     */
    suspend fun phoneLanguages(
        context: Context,
        config: SpeechConfig = SpeechConfig(),
    ): Outcome<List<String>, SpeechError> {
        val engine = system(context, config)
        return try {
            engine.offlineLanguages()
        } finally {
            engine.release()
        }
    }

    /**
     * The phone's best offline voice for [lang] ([SystemSpeechEngine.bestVoice]), or why there is none: what a beta
     * language's scanned page offers instead of ours (2026-10-31). Binds the phone's TTS for this call only.
     */
    suspend fun phoneVoice(
        context: Context,
        lang: Lang,
        config: SpeechConfig = SpeechConfig(),
    ): Outcome<VoiceId, SpeechError> {
        val engine = system(context, config)
        return try {
            engine.bestVoice(lang)
        } finally {
            engine.release()
        }
    }

    /** The bundled voice's `voice.properties`, written by the build (ADR-014). */
    internal fun readBundledSpec(assets: AssetManager): Outcome<SherpaVoiceSpec, String> =
        readBundledManifest(assets).flatMap { SherpaVoiceManifest.parse(it) }

    /** The bundled voice's credits for the licences screen (P3-06); null if the build wrote none. */
    internal fun readBundledCredits(assets: AssetManager): VoiceCredits? =
        readBundledManifest(assets).getOrNull()?.let { VoiceCredits.parse(it) }

    private fun readBundledManifest(assets: AssetManager): Outcome<Properties, String> {
        val path = "$BUNDLED_ASSET_DIR/${SherpaVoiceManifest.FILE_NAME}"
        return try {
            Outcome.Success(assets.open(path).reader(Charsets.UTF_8).use { Properties().apply { load(it) } })
        } catch (e: IOException) {
            Outcome.Failure("assets/$path unreadable: ${e.message}")
        }
    }
}
