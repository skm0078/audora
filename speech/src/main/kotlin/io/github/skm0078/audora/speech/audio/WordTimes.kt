// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/WordTimes.kt
// Purpose: When each word of a sentence starts in its voice's audio (CF-10): punctuation pinned to the voice's pauses.

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.WordTimingConfig
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Times the words of a sentence from the voice's own audio, since no engine reports them (ADR 2026-10-26-word-timings).
 *
 * The speech runs from the first loud frame to the last. Its pauses (quiet runs of at least `minPauseMs` inside it)
 * are matched in order to the sentence's punctuation, each mark to the pause nearest where the letters put it and
 * within the tolerance, so a breath that matches no mark is passed over and a mark the voice ran through is not
 * forced. Between two matched pauses the words share the time by their letters, as before. A pause at a comma was
 * where the old even spread drifted most (±150 ms); here the highlight waits through it and starts the next word on
 * the voice.
 *
 * Words are what whitespace separates, exactly as the reader's highlight counts them, so index i here is the
 * reader's word i. Pure and cheap: one pass over the samples and a small match.
 */
class WordTimes(private val config: WordTimingConfig = WordTimingConfig()) {
    /**
     * Each word's start in [audio], in ms from its first sample, one per whitespace-separated word of [text]; null
     * when [text] has no words or [audio] no sound (the reader then spreads by letters alone).
     */
    fun of(text: String, audio: PcmAudio): List<Long>? {
        val words = WORD.findAll(text).map { it.value }.toList()
        val frames = frameLoudness(audio)
        val quiet = if (frames.isEmpty()) 0.0 else quietLevel(frames)
        val first = frames.indexOfFirst { it >= quiet }
        val last = frames.indexOfLast { it >= quiet }
        if (words.isEmpty() || first < 0) return null
        val span = Span(first.toLong() * config.frameMs, (last + 1).toLong() * config.frameMs)
        val weights = words.map(::weightOf)
        val pauses = pausesOf(frames, quiet, first, last)
        val breaks = words.indices.filter { it < words.lastIndex && endsClause(words[it]) }
        val matched = match(breaks, pauses, expectedTimes(breaks, weights, span), span)
        return startsOf(weights, matched, span)
    }

    /** The speech's start and end in ms. */
    private data class Span(val startMs: Long, val endMs: Long) {
        val lengthMs: Long get() = endMs - startMs
    }

    /** A pause inside the speech, in ms. */
    private data class Pause(val startMs: Long, val endMs: Long) {
        val middleMs: Long get() = (startMs + endMs) / 2
    }

    /** RMS of each [WordTimingConfig.frameMs] frame of [audio]. */
    private fun frameLoudness(audio: PcmAudio): DoubleArray {
        val size = (audio.sampleRate.toLong() * config.frameMs / MS_PER_SECOND).toInt().coerceAtLeast(1)
        val count = audio.samples.size / size
        return DoubleArray(count) { f ->
            var sum = 0.0
            for (i in f * size until (f + 1) * size) {
                val s = audio.samples[i].toDouble()
                sum += s * s
            }
            sqrt(sum / size)
        }
    }

    /** Below this a frame is quiet: a share of the speech's typical loud frame, and never below the floor. */
    private fun quietLevel(frames: DoubleArray): Double {
        val sorted = frames.sorted()
        val loud = sorted[((sorted.size - 1) * config.loudPercentile).toInt()]
        return maxOf(loud * config.quietFraction, config.quietFloor.toDouble())
    }

    /** Quiet runs of at least [WordTimingConfig.minPauseMs] strictly inside frames [first]..[last]. */
    private fun pausesOf(frames: DoubleArray, quiet: Double, first: Int, last: Int): List<Pause> {
        val minFrames = config.minPauseMs / config.frameMs
        val pauses = mutableListOf<Pause>()
        var runStart = -1
        for (f in first..last) {
            if (frames[f] < quiet) {
                if (runStart < 0) runStart = f
            } else {
                if (runStart >= 0 && f - runStart >= minFrames) {
                    pauses += Pause(runStart.toLong() * config.frameMs, f.toLong() * config.frameMs)
                }
                runStart = -1
            }
        }
        return pauses
    }

    /** Where the letters alone put the end of each word in [breaks]. */
    private fun expectedTimes(breaks: List<Int>, weights: List<Int>, span: Span): List<Long> {
        val total = weights.sum().toDouble()
        val before = weights.runningFold(0) { sum, w -> sum + w }
        return breaks.map { span.startMs + (span.lengthMs * before[it + 1] / total).toLong() }
    }

    /**
     * Pairs breaks with pauses, both in order, to the most total closeness: an LCS-style table where a pair counts
     * only within the tolerance, scored by how much of the tolerance it leaves. Returns break word index to pause.
     */
    private fun match(breaks: List<Int>, pauses: List<Pause>, expected: List<Long>, span: Span): Map<Int, Pause> {
        if (breaks.isEmpty() || pauses.isEmpty()) return emptyMap()
        val tolerance = maxOf(config.minToleranceMs.toLong(), (span.lengthMs * config.toleranceFraction).toLong())
        val n = breaks.size
        val m = pauses.size
        val best = Array(n + 1) { LongArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                val gap = abs(expected[i] - pauses[j].middleMs)
                val pair = if (gap <= tolerance) tolerance - gap + 1 + best[i + 1][j + 1] else Long.MIN_VALUE
                best[i][j] = maxOf(best[i + 1][j], best[i][j + 1], pair)
            }
        }
        val matched = mutableMapOf<Int, Pause>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            val gap = abs(expected[i] - pauses[j].middleMs)
            when {
                gap <= tolerance && best[i][j] == tolerance - gap + 1 + best[i + 1][j + 1] -> {
                    matched[breaks[i]] = pauses[j]
                    i++
                    j++
                }

                best[i][j] == best[i + 1][j] -> i++

                else -> j++
            }
        }
        return matched
    }

    /** Each word's start: the letters share each stretch of speech between the span's ends and matched pauses. */
    private fun startsOf(weights: List<Int>, matched: Map<Int, Pause>, span: Span): List<Long> {
        val starts = ArrayList<Long>(weights.size)
        var segmentFirst = 0
        var segmentStart = span.startMs
        while (segmentFirst < weights.size) {
            val breakAt = matched.keys.filter { it >= segmentFirst }.minOrNull()
            val segmentLast = breakAt ?: weights.lastIndex
            val segmentEnd = breakAt?.let { matched.getValue(it).startMs } ?: span.endMs
            val total = (segmentFirst..segmentLast).sumOf { weights[it] }.toDouble()
            var before = 0
            for (w in segmentFirst..segmentLast) {
                starts += segmentStart + ((segmentEnd - segmentStart) * before / total).toLong()
                before += weights[w]
            }
            segmentFirst = segmentLast + 1
            segmentStart = breakAt?.let { matched.getValue(it).endMs } ?: span.endMs
        }
        return starts
    }

    private companion object {
        /** What [Char.isWhitespace] separates, as the reader's highlight counts words. */
        val WORD = Regex("[^\\p{javaWhitespace}\\p{Z}]+")

        /** Marks the voice pauses at: commas and stops of Latin, Devanagari and Bengali text, dashes, ellipses. */
        const val CLAUSE_MARKS = ",;:.!?।॥—–…"
        const val MS_PER_SECOND = 1_000L

        /** A word's share of the time: its letters, digits and vowel signs, and at least one. */
        fun weightOf(word: String): Int = word.count { it.isLetterOrDigit() || Character.getType(it).isMark() }
            .coerceAtLeast(1)

        /** The word ends a clause: its trailing punctuation (after any closing quote or bracket) holds a mark. */
        fun endsClause(word: String): Boolean =
            word.takeLastWhile { !it.isLetterOrDigit() && !Character.getType(it).isMark() }.any { it in CLAUSE_MARKS }

        fun Int.isMark(): Boolean = this == Character.NON_SPACING_MARK.toInt() ||
            this == Character.COMBINING_SPACING_MARK.toInt() || this == Character.ENCLOSING_MARK.toInt()
    }
}
