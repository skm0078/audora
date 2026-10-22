// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/VoiceShaper.kt
// Purpose: Even loudness with a soft limiter, voice boost and pause trim on synthesized speech (plan items 9, 10).

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.VoiceShapingConfig
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure and stateless, one sentence at a time. Leveling is one gain for the whole sentence (no compression inside it),
 * so the voice's own emphasis and quieter asides (RI-6) keep their shape; only peaks above the knee are bent.
 */
class VoiceShaper(private val config: VoiceShapingConfig = VoiceShapingConfig()) {
    private val ceiling = PCM16_FULL_SCALE * dbToLinear(config.ceilingDbfs)
    private val knee = ceiling * dbToLinear(-config.kneeDb)

    /** [audio] brought to the target loudness when [evenLoudness], plus [boostDb]; itself when nothing changes. */
    fun level(audio: PcmAudio, evenLoudness: Boolean, boostDb: Int): PcmAudio {
        val evenDb = if (evenLoudness) evenGainDb(audio) else 0f
        val gainDb = evenDb + boostDb
        if (gainDb == 0f) return audio
        val gain = dbToLinear(gainDb)
        val out = ShortArray(audio.samples.size) { i ->
            softLimit(audio.samples[i] * gain).roundToInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
        return PcmAudio(out, audio.sampleRate)
    }

    /** The loudness of [audio]'s speech frames in dBFS; null when no frame is above the speech gate. */
    fun speechDbfs(audio: PcmAudio): Float? {
        val frame = frameSamples(audio.sampleRate)
        var energy = 0.0
        var counted = 0L
        audio.samples.indices.step(frame).forEach { start ->
            val end = minOf(start + frame, audio.samples.size)
            val frameEnergy = energyOf(audio.samples, start, end)
            if (dbfs(frameEnergy, end - start) >= config.speechGateDbfs) {
                energy += frameEnergy
                counted += end - start
            }
        }
        return if (counted == 0L) null else dbfs(energy, counted)
    }

    /**
     * [audio] with the voice's own long silences shortened: inside, to [VoiceShapingConfig.longPauseMs] (half kept
     * from each side, so the fade-out and the next breath stay); at the start and end, to
     * [VoiceShapingConfig.edgeMs]. All-silent audio is returned as it is.
     */
    fun trimPauses(audio: PcmAudio): PcmAudio {
        val samples = audio.samples
        val frame = frameSamples(audio.sampleRate)
        val silent = BooleanArray((samples.size + frame - 1) / frame) { f ->
            val start = f * frame
            val end = minOf(start + frame, samples.size)
            dbfs(energyOf(samples, start, end), end - start) < config.silenceDbfs
        }
        val keep = if (silent.all { it }) null else keptRanges(silent, frame, samples.size, audio.sampleRate)
        val total = keep?.sumOf { it.last - it.first + 1 } ?: samples.size
        if (keep == null || total == samples.size) return audio
        val out = ShortArray(total)
        var offset = 0
        keep.forEach { range ->
            samples.copyInto(out, offset, range.first, range.last + 1)
            offset += range.last - range.first + 1
        }
        return PcmAudio(out, audio.sampleRate)
    }

    private fun keptRanges(silent: BooleanArray, frame: Int, size: Int, sampleRate: Int): List<IntRange> {
        val longPause = msToSamples(config.longPauseMs, sampleRate)
        val edge = msToSamples(config.edgeMs, sampleRate)
        val ranges = ArrayList<IntRange>()
        var f = 0
        while (f < silent.size) {
            val runEnd = (f until silent.size).firstOrNull { silent[it] != silent[f] } ?: silent.size
            val start = f * frame
            val end = minOf(runEnd * frame, size) // exclusive
            val length = end - start
            when {
                !silent[f] -> ranges.add(start until end)

                start == 0 -> ranges.add(maxOf(start, end - edge) until end)

                end == size -> ranges.add(start until minOf(end, start + edge))

                length <= longPause -> ranges.add(start until end)

                else -> {
                    ranges.add(start until start + longPause / 2)
                    ranges.add(end - (longPause - longPause / 2) until end)
                }
            }
            f = runEnd
        }
        return ranges.filterNot { it.isEmpty() }
    }

    private fun evenGainDb(audio: PcmAudio): Float =
        speechDbfs(audio)?.let { (config.targetDbfs - it).coerceIn(-config.maxGainDb, config.maxGainDb) } ?: 0f

    private fun softLimit(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= knee) return x
        val room = ceiling - knee
        return sign(x) * (knee + room * tanh((magnitude - knee) / room))
    }

    private fun frameSamples(sampleRate: Int): Int = maxOf(1, msToSamples(config.frameMs, sampleRate))

    private companion object {
        const val PCM16_FULL_SCALE = 32_768f
        const val SHORT_MIN = Short.MIN_VALUE.toInt()
        const val SHORT_MAX = Short.MAX_VALUE.toInt()
        const val MS_PER_SECOND = 1_000L
        const val DB_PER_DECADE = 20f
        const val SILENCE_DBFS = -120f

        fun dbToLinear(db: Float): Float = 10f.pow(db / DB_PER_DECADE)

        fun msToSamples(ms: Int, sampleRate: Int): Int = (ms * sampleRate.toLong() / MS_PER_SECOND).toInt()

        fun energyOf(samples: ShortArray, start: Int, end: Int): Double {
            var sum = 0.0
            for (i in start until end) sum += samples[i].toDouble() * samples[i]
            return sum
        }

        fun dbfs(energy: Double, count: Long): Float {
            if (count == 0L || energy <= 0.0) return SILENCE_DBFS
            val rms = sqrt(energy / count)
            return (DB_PER_DECADE * log10(rms / PCM16_FULL_SCALE)).toFloat()
        }

        fun dbfs(energy: Double, count: Int): Float = dbfs(energy, count.toLong())
    }
}
