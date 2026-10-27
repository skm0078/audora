// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/TutorRunStats.kt
// Purpose: Counts one tutor run for the log (Q-5): microphone level, decoding speed and how stretches were taken.

package io.github.skm0078.audora.playback.tutor

import io.github.skm0078.audora.core.tutor.ReadAlong
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What "Share logs" needs to tell why a run went wrong without the reader's words: a quiet or clipping microphone,
 * decoding slower than speech (audio queued up), a recogniser that heard nothing, or stretches that never moved the
 * place. Numbers only; never what was heard or what the page says. Audio and stretches are counted on the decoding
 * coroutine; [queueFull] on the microphone's; [summary] is read once both have stopped.
 */
internal class TutorRunStats(private val sampleRateHz: Int, start: ReadAlong) {
    @Volatile
    private var latest = start

    private var samples = 0L
    private var squares = 0.0
    private var peak = 0f
    private var chunks = 0
    private var quietChunks = 0
    private var clipped = 0L
    private var decodeNanos = 0L
    private var partial = 0
    private var finals = 0
    private val taken = ReadAlong.Taken.entries.associateWith { 0 }.toMutableMap()
    private var finalWords = 0

    /** The decoder fell behind and the microphone had to wait: audio may have been lost meanwhile. */
    val queueFull = AtomicInteger()

    /** How long the speech model took to load, once known. */
    var modelLoadMs: Long = -1

    fun audio(chunk: FloatArray) {
        var sum = 0.0
        chunk.forEach { sample ->
            val size = abs(sample)
            sum += sample * sample
            if (size > peak) peak = size
            if (size >= CLIP_LEVEL) clipped++
        }
        samples += chunk.size
        squares += sum
        chunks++
        if (chunk.isNotEmpty() && dbfs(sqrt(sum / chunk.size)) < QUIET_DBFS) quietChunks++
    }

    fun decoded(nanos: Long) {
        decodeNanos += nanos
    }

    fun stretch(step: ReadAlong.Step, final: Boolean) {
        latest = step.along
        if (!final) {
            partial++
            return
        }
        finals++
        finalWords += step.words
        taken[step.taken] = taken.getValue(step.taken) + 1
    }

    /** One line for the log at the end of a run, however it ended (a reader closing a stuck tutor most of all). */
    fun summary(): String {
        val along = latest
        val seconds = samples.toDouble() / sampleRateHz
        val level = if (samples == 0L) null else dbfs(sqrt(squares / samples))
        val realtime = if (seconds > 0) decodeNanos / NANOS_PER_SECOND / seconds else 0.0
        val quietPercent = if (chunks == 0) 0 else quietChunks * PERCENT / chunks
        return "Run summary: audio=${"%.1f".format(Locale.ROOT, seconds)}s, level=${level?.roundToInt()} dBFS, " +
            "peak=${dbfs(peak.toDouble()).roundToInt()} dBFS, quiet=$quietPercent%, clipped=$clipped, " +
            "decode=${"%.2f".format(Locale.ROOT, realtime)}x realtime, queueFull=${queueFull.get()}, " +
            "modelLoadMs=$modelLoadMs, stretches final=$finals partial=$partial " +
            "moved=${taken[ReadAlong.Taken.MOVED]} held=${taken[ReadAlong.Taken.HELD]} " +
            "repeat=${taken[ReadAlong.Taken.REPEAT]}, heardWords=$finalWords, " +
            "place=${along.next}/${along.words.size}, finished=${along.finished}"
    }

    private companion object {
        /** Full scale is 0 dBFS; a speaking voice at arm's length sits around -35 to -20. */
        const val QUIET_DBFS = -50.0
        const val CLIP_LEVEL = 0.99f
        const val SILENCE_DBFS = -120.0
        const val DB_PER_DECADE = 20
        const val PERCENT = 100
        const val NANOS_PER_SECOND = 1e9

        fun dbfs(rms: Double): Double = if (rms <= 0.0) SILENCE_DBFS else DB_PER_DECADE * log10(rms)
    }
}
