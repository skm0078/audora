// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusMixer.kt
// Purpose: Mixes the focus sound into the voice's audio (plan item 72), dipping it whenever the voice speaks.

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.config.FocusSoundConfig
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Works on the voice's own 16-bit PCM as it goes to the speaker, so the dip follows the voice sample by sample: no
 * second player to keep in step, and the sound pauses, stops and keeps audio focus with the voice. One audio thread.
 */
class FocusMixer(
    val sampleRate: Int,
    private val config: FocusSoundConfig = FocusSoundConfig(),
    private val seed: Long = DEFAULT_SEED,
) {
    private val holdFrames = frames(config.holdMs)
    private val duckStep = step(config.duckMs)
    private val releaseStep = step(config.releaseMs)
    private val switchFall = config.maxGain / maxOf(1, frames(config.switchMs))
    private val voiceFall = step(VOICE_FALL_MS)
    private var playing = FocusSound.OFF
    private var source: NoiseSource? = null
    private var gain = 0f
    private var voice = 0f
    private var held = 0

    init {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
    }

    /** The sound being heard now (it fades out before another starts). */
    val sound: FocusSound get() = playing

    /** Mixes into the first [count] samples of [samples] (interleaved, [channels] per frame), in place. */
    fun mix(samples: ShortArray, count: Int, channels: Int, options: FocusSoundOptions) {
        require(channels > 0) { "channels must be > 0, was $channels" }
        require(count in 0..samples.size) { "count $count outside 0..${samples.size}" }
        var frame = 0
        while (frame + channels <= count) {
            val bed = nextBed(peak(samples, frame, channels), options)
            if (bed != 0f) {
                for (i in frame until frame + channels) samples[i] = clip(samples[i] + bed * Short.MAX_VALUE)
            }
            frame += channels
        }
    }

    /**
     * The same sound carried on at another [rate] (a voice of another sample rate took over): its level, and whether
     * the voice was speaking, go on, so the sound neither restarts nor fades in again.
     */
    fun continuedAt(rate: Int): FocusMixer = FocusMixer(rate, config, seed).also { next ->
        next.playing = playing
        next.source = source?.let { FocusNoise.of(playing, rate, seed) }
        next.gain = gain
        next.voice = voice
        next.held = (held.toLong() * rate / sampleRate).toInt()
    }

    /** The audio stopped for a while (a slow render, a seek): the sound comes back from silence, fading in. */
    fun fadeIn() {
        gain = 0f
    }

    /**
     * [FocusSoundConfig.tailMs] of the sound alone, fading to silence: played after the voice's last audio so the
     * sound does not stop dead. Empty with the sound off or already silent.
     */
    fun tail(channels: Int): ShortArray {
        val noise = source
        val frames = frames(config.tailMs)
        if (noise == null || gain < SILENT || frames == 0) return ShortArray(0)
        val start = gain
        val out = ShortArray(frames * channels)
        for (frame in 0 until frames) {
            val bed = noise.next() * start * (frames - frame) / frames
            for (c in 0 until channels) out[frame * channels + c] = clip(bed * Short.MAX_VALUE)
        }
        gain = 0f
        return out
    }

    private fun nextBed(peak: Float, options: FocusSoundOptions): Float {
        trackVoice(peak)
        val switching = options.sound != playing
        val target = when {
            switching || !options.on -> 0f
            held > 0 -> options.level * config.maxGain * config.duckedFraction
            else -> options.level * config.maxGain
        }
        if (switching) {
            // Linear, so a switch takes [FocusSoundConfig.switchMs] at most, whatever the level.
            gain = maxOf(0f, gain - switchFall)
            if (gain < SILENT) switchTo(options.sound)
        } else {
            gain += (target - gain) * (if (target < gain) duckStep else releaseStep)
        }
        val noise = source ?: return 0f
        return if (target == 0f && gain < SILENT) 0f else noise.next() * gain
    }

    private fun switchTo(sound: FocusSound) {
        playing = sound
        source = FocusNoise.of(sound, sampleRate, seed)
        gain = 0f
    }

    /** The voice's envelope: up at once, down slowly; "speaking" holds across the short gaps between words. */
    private fun trackVoice(peak: Float) {
        voice = if (peak > voice) peak else voice + (peak - voice) * voiceFall
        if (voice > config.voiceThreshold) {
            held = holdFrames
        } else if (held > 0) {
            held--
        }
    }

    private fun peak(samples: ShortArray, from: Int, channels: Int): Float {
        var peak = 0
        for (i in from until from + channels) peak = maxOf(peak, abs(samples[i].toInt()))
        return peak.toFloat() / Short.MAX_VALUE
    }

    private fun frames(ms: Int): Int = (sampleRate.toLong() * ms / MS_PER_SECOND).toInt()

    /** The share of the distance to its target a one-pole fade covers per frame, for a time constant of [ms]. */
    private fun step(ms: Int): Float = 1f - exp(-1.0 / maxOf(1, frames(ms))).toFloat()

    private companion object {
        const val DEFAULT_SEED = 72L
        const val VOICE_FALL_MS = 20
        const val SILENT = 1e-4f
        const val MS_PER_SECOND = 1_000L
    }
}

private fun clip(value: Float): Short =
    value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
