// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/config/SpikeConfig.kt
// Purpose: Every tunable of one S3 test run, validated in one place so a bad value fails before playback starts.

package io.github.skm0078.audora.spike.playback.config

/** How the fake engine spends its render time. */
enum class EngineWork {
    /** Sleeps. Cheap on battery; still stalls if the CPU suspends, which is what S3 must catch. */
    SLEEP,

    /** Spins the CPU like a real neural voice would. Exposes thermal and OEM throttling of busy background apps. */
    BUSY,
}

data class SpikeConfig(
    /** Render time ÷ audio time. Above 1.0 the engine is slower than real time and the player must wait. */
    val realTimeFactor: Double = DEFAULT_REAL_TIME_FACTOR,
    val engineWork: EngineWork = EngineWork.SLEEP,
    /** Sentences in the fake book. The default is far longer than a 60-minute run, so ENDED is never legitimate. */
    val unitCount: Int = DEFAULT_UNIT_COUNT,
    /** Playlist items appended at a time, like the real app appending one extracted page. */
    val unitsPerPage: Int = DEFAULT_UNITS_PER_PAGE,
    /** Units rendered ahead of the one playing (Build Spec §6: 3 for fast voices). */
    val prefetchUnits: Int = DEFAULT_PREFETCH_UNITS,
    /** Media3's experimental per-stream progression (Build Spec §6 asks S3 to try it). */
    val perStreamProgression: Boolean = false,
    /** Holds our own PARTIAL_WAKE_LOCK while playWhenReady (Build Spec §6 "belt and braces"). */
    val ownWakeLock: Boolean = false,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val minUnitMs: Long = DEFAULT_MIN_UNIT_MS,
    val maxUnitMs: Long = DEFAULT_MAX_UNIT_MS,
    val seed: Long = DEFAULT_SEED,
) {
    /** Human-readable problems; empty when the config can run. */
    fun problems(): List<String> = buildList {
        if (realTimeFactor !in MIN_REAL_TIME_FACTOR..MAX_REAL_TIME_FACTOR) {
            add("Real-time factor must be between $MIN_REAL_TIME_FACTOR and $MAX_REAL_TIME_FACTOR, was $realTimeFactor")
        }
        if (unitCount < 1) add("Book must have at least 1 unit, was $unitCount")
        if (unitsPerPage < 1) add("Units per page must be at least 1, was $unitsPerPage")
        if (prefetchUnits < 1) add("Prefetch must be at least 1 unit, was $prefetchUnits")
        if (sampleRateHz <= 0) add("Sample rate must be positive, was $sampleRateHz")
        if (minUnitMs <= 0 || maxUnitMs < minUnitMs) {
            add("Unit length range is invalid: $minUnitMs..$maxUnitMs ms")
        }
    }

    /** One line for logs and the shared report. */
    fun summary(): String = "rtf=$realTimeFactor engine=$engineWork units=$unitCount page=$unitsPerPage " +
        "prefetch=$prefetchUnits perStream=$perStreamProgression ownWakeLock=$ownWakeLock " +
        "rate=${sampleRateHz}Hz unit=$minUnitMs..${maxUnitMs}ms seed=$seed"

    companion object {
        const val DEFAULT_REAL_TIME_FACTOR = 1.2
        const val MIN_REAL_TIME_FACTOR = 0.05
        const val MAX_REAL_TIME_FACTOR = 5.0
        const val DEFAULT_UNIT_COUNT = 2_000
        const val DEFAULT_UNITS_PER_PAGE = 20
        const val DEFAULT_PREFETCH_UNITS = 3

        /** Piper medium voices render at 22.05 kHz mono. */
        const val DEFAULT_SAMPLE_RATE_HZ = 22_050
        const val DEFAULT_MIN_UNIT_MS = 2_500L
        const val DEFAULT_MAX_UNIT_MS = 9_000L
        const val DEFAULT_SEED = 42L
    }
}
