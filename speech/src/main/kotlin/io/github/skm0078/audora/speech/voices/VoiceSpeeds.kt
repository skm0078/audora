// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceSpeeds.kt
// Purpose: How fast each voice renders on this phone, measured during playback and kept across restarts (P4-02).

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceSpeedConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.VoiceId
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One voice's measured speed on this phone.
 *
 * @property rtf real-time factor: seconds spent rendering per second of audio (below 1 keeps up).
 * @property charsPerSecond how much text a second of its audio reads; sizes the render-ahead window before a
 *   sentence is rendered.
 * @property measuredSeconds audio measured so far (grows without bound; only compared with a threshold).
 */
data class VoiceSpeed(val rtf: Double, val charsPerSecond: Double, val measuredSeconds: Double) {
    /** Build Spec §3: offered only where measured RTF is below the limit, judged once enough audio was measured. */
    fun tooSlow(config: VoiceSpeedConfig): Boolean =
        measuredSeconds >= config.minMeasuredSeconds && rtf >= config.maxUsableRtf

    /**
     * Folds in one render. The weight is the render's share of [VoiceSpeedConfig.horizonSeconds], so long sentences
     * count more than short ones and the average follows the phone's current state (heat, battery saver).
     */
    fun plus(sample: SpeedSample, config: VoiceSpeedConfig): VoiceSpeed {
        val weight = (sample.audioSeconds / config.horizonSeconds).coerceAtMost(1.0)
        return VoiceSpeed(
            rtf = rtf + (sample.rtf - rtf) * weight,
            charsPerSecond = charsPerSecond + (sample.charsPerSecond - charsPerSecond) * weight,
            measuredSeconds = measuredSeconds + sample.audioSeconds,
        )
    }

    companion object {
        fun first(sample: SpeedSample): VoiceSpeed = VoiceSpeed(sample.rtf, sample.charsPerSecond, sample.audioSeconds)
    }
}

/** One measured render: [chars] of text took [renderSeconds] and gave [audioSeconds] of audio. */
data class SpeedSample(val chars: Int, val renderSeconds: Double, val audioSeconds: Double) {
    init {
        require(chars > 0) { "chars must be > 0, was $chars" }
        require(renderSeconds >= 0) { "renderSeconds must be >= 0, was $renderSeconds" }
        require(audioSeconds > 0) { "audioSeconds must be > 0, was $audioSeconds" }
    }

    val rtf: Double get() = renderSeconds / audioSeconds
    val charsPerSecond: Double get() = chars / audioSeconds
}

/** Where measured speeds are read and recorded: [VoiceSpeeds] in the app, a map in tests. Thread-safe. */
interface MeasuredSpeeds {
    fun get(voice: VoiceId): VoiceSpeed?

    fun record(voice: VoiceId, sample: SpeedSample)
}

/**
 * Measured speeds per voice, in memory for the render thread and the Voices screen, saved to a small properties file
 * every [VoiceSpeedConfig.saveEveryRenders] renders. Speeds belong to this phone, so the file sits with the voices in
 * no-backup storage. A missing or unreadable file means "not measured yet", never an error. Thread-safe.
 */
class VoiceSpeeds(private val file: File, private val config: VoiceSpeedConfig = VoiceSpeedConfig()) : MeasuredSpeeds {
    private val state = MutableStateFlow(read())
    private val lock = Any()
    private var unsaved = 0

    val all: StateFlow<Map<VoiceId, VoiceSpeed>> = state.asStateFlow()

    override fun get(voice: VoiceId): VoiceSpeed? = state.value[voice]

    fun tooSlow(voice: VoiceId): Boolean = get(voice)?.tooSlow(config) == true

    override fun record(voice: VoiceId, sample: SpeedSample) {
        state.update { speeds ->
            val updated = speeds[voice]?.plus(sample, config) ?: VoiceSpeed.first(sample)
            speeds + (voice to updated)
        }
        val due = synchronized(lock) {
            unsaved++
            (unsaved >= config.saveEveryRenders).also { if (it) unsaved = 0 }
        }
        if (due) save()
    }

    /** A deleted voice's measurement goes with it, so a re-download on a cooler day starts fresh. */
    fun forget(voice: VoiceId) {
        state.update { it - voice }
        save()
    }

    /** Writes the current speeds (temp file + rename). Returns false, after logging, if it could not. */
    fun save(): Boolean = synchronized(lock) {
        val properties = Properties()
        state.value.forEach { (voice, speed) ->
            val key = "${voice.engine.name}:${voice.name}"
            properties.setProperty("$key.$RTF", speed.rtf.toString())
            properties.setProperty("$key.$CPS", speed.charsPerSecond.toString())
            properties.setProperty("$key.$SECONDS", speed.measuredSeconds.toString())
        }
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writer(Charsets.UTF_8).use { properties.store(it, null) }
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            true
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save voice speeds", e)
            false
        }
    }

    private fun read(): Map<VoiceId, VoiceSpeed> {
        val properties = Properties()
        if (file.isFile) {
            try {
                file.reader(Charsets.UTF_8).use { properties.load(it) }
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Voice speeds unreadable, measuring again: ${e.message}")
                properties.clear()
            }
        }
        return properties.stringPropertyNames()
            .filter { it.endsWith(".$RTF") }
            .mapNotNull { name -> parse(properties, name.removeSuffix(".$RTF")) }
            .toMap()
    }

    private fun parse(properties: Properties, key: String): Pair<VoiceId, VoiceSpeed>? {
        fun number(field: String, min: Double): Double? =
            properties.getProperty("$key.$field")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= min }

        val engine = EngineId.entries.firstOrNull { it.name == key.substringBefore(':') }
        val voice = engine?.let { VoiceId(it, key.substringAfter(':', missingDelimiterValue = "")) }
            ?.takeIf { it.name.isNotBlank() }
        val rtf = number(RTF, 0.0)
        val seconds = number(SECONDS, 0.0)
        val cps = number(CPS, Double.MIN_VALUE)
        val speed = if (rtf != null && cps != null && seconds != null) VoiceSpeed(rtf, cps, seconds) else null
        val entry = if (voice != null && speed != null) voice to speed else null
        if (entry == null) AudoraLog.w(TAG, "Ignoring unreadable speed entry '$key'")
        return entry
    }

    private companion object {
        const val TAG = "Speech.VoiceSpeeds"
        const val RTF = "rtf"
        const val CPS = "charsPerSecond"
        const val SECONDS = "measuredSeconds"
    }
}
