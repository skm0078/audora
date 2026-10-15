// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchRunner.kt
// Purpose: Measures one engine: load time, cold first sentence, then warm renders of every text of its language.
//          Pure Kotlin over BenchEngine, a clock and a device probe, so it is unit-tested with a fake engine.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.voices.catalog.BenchText
import io.github.skm0078.audora.spike.voices.catalog.TextKind
import io.github.skm0078.audora.spike.voices.engine.BenchEngine
import io.github.skm0078.audora.spike.voices.engine.EngineException
import io.github.skm0078.audora.spike.voices.engine.Rendered

/** Monotonic milliseconds (SystemClock.elapsedRealtime on the phone). */
fun interface BenchClock {
    fun nowMs(): Long
}

/** Device readings taken around each run. Nullable where the platform may not report them. */
interface DeviceProbe {
    fun rssMb(): Long?

    fun thermalStatus(): String
}

/** Receives the listening sample of a voice: chosen texts joined with short silences. */
fun interface SampleSink {
    fun save(voiceId: String, sample: Rendered)
}

class BenchRunner(
    private val clock: BenchClock,
    private val probe: DeviceProbe,
    private val config: BenchConfig = BenchConfig(),
) {
    fun run(
        engine: BenchEngine,
        texts: List<BenchText>,
        sampleSink: SampleSink?,
        isCancelled: () -> Boolean,
    ): VoiceRun {
        val first = texts.firstOrNull { it.kind == TextKind.FIRST }
            ?: return failed(engine, "no first-sentence text for ${engine.lang}")
        var run = VoiceRun(
            voiceId = engine.voiceId,
            label = engine.label,
            lang = engine.lang,
            threads = engine.threads,
            rssBeforeMb = probe.rssMb(),
            thermalStart = probe.thermalStatus(),
        )
        val warm = mutableListOf<TextTiming>()
        try {
            val loadMs = timed { engine.load() }.first
            val coldFirstMs = timed { engine.render(first.text) }.first
            run = run.copy(loadMs = loadMs, coldFirstMs = coldFirstMs)
            val samples = renderAll(engine, texts, warm, isCancelled)
            run = if (warm.size < texts.size) {
                run.copy(warm = warm, error = "cancelled")
            } else {
                saveSample(engine.voiceId, samples, sampleSink)
                run.copy(warm = warm)
            }
        } catch (e: EngineException) {
            AudoraLog.e(TAG, "Benchmark of ${engine.voiceId} (threads ${engine.threads}) failed", e)
            run = run.copy(warm = warm, error = e.message ?: "engine error")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // The JNI layer reports native failures as unchecked exceptions; one bad voice must not end the run.
            AudoraLog.e(TAG, "Benchmark of ${engine.voiceId} (threads ${engine.threads}) crashed", e)
            run = run.copy(warm = warm, error = "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            run = run.copy(rssAfterMb = probe.rssMb(), thermalEnd = probe.thermalStatus(), details = engine.details)
            engine.release()
        }
        return run
    }

    /** Renders every text warm, appending timings to [warm]; returns the audio kept for the listening sample. */
    private fun renderAll(
        engine: BenchEngine,
        texts: List<BenchText>,
        warm: MutableList<TextTiming>,
        isCancelled: () -> Boolean,
    ): Map<TextKind, Rendered> {
        val samples = mutableMapOf<TextKind, Rendered>()
        for (text in texts) {
            if (isCancelled()) break
            val (ms, audio) = timed { engine.render(text.text) }
            warm += TextTiming(text.id, text.kind, ms, audio.audioMs)
            if (text.kind in config.sampleKinds) samples[text.kind] = audio
        }
        return samples
    }

    private fun saveSample(voiceId: String, samples: Map<TextKind, Rendered>, sink: SampleSink?) {
        val sample = joinSample(samples) ?: return
        sink?.save(voiceId, sample)
    }

    private fun joinSample(samples: Map<TextKind, Rendered>): Rendered? {
        val parts = config.sampleKinds.mapNotNull { samples[it] }
        if (parts.isEmpty()) return null
        val rate = parts.first().sampleRate
        val gap = ShortArray((rate.toLong() * config.sampleGapMs / MS_PER_SECOND).toInt())
        val pcm = parts.flatMapIndexed { i, part -> if (i == 0) listOf(part.pcm) else listOf(gap, part.pcm) }
            .reduce { acc, next -> acc + next }
        return Rendered(pcm, rate)
    }

    private fun failed(engine: BenchEngine, reason: String) =
        VoiceRun(engine.voiceId, engine.label, engine.lang, engine.threads, error = reason)

    private inline fun <T> timed(block: () -> T): Pair<Long, T> {
        val start = clock.nowMs()
        val value = block()
        return (clock.nowMs() - start) to value
    }

    private companion object {
        const val TAG = "S1.Bench"
        const val MS_PER_SECOND = 1_000L
    }
}
