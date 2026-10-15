// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchPlan.kt
// Purpose: Decides which runs a benchmark makes: every installed catalog voice at each thread count, plus the
//          phone's system voice per language. The listening sample is saved from one run per voice only.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.catalog.Catalog
import io.github.skm0078.audora.spike.voices.catalog.VoiceSpec

sealed interface PlannedRun {
    val lang: String
    val saveSample: Boolean

    data class Neural(val spec: VoiceSpec, val threads: Int, override val saveSample: Boolean) : PlannedRun {
        override val lang: String get() = spec.lang
    }

    data class System(override val lang: String) : PlannedRun {
        override val saveSample: Boolean get() = true
    }
}

object BenchPlan {
    fun build(catalog: Catalog, isInstalled: (String) -> Boolean, config: BenchConfig): List<PlannedRun> {
        val sampleThreads = config.threadCounts.max()
        return catalog.languages.flatMap { lang ->
            val neural = catalog.voices
                .filter { it.lang == lang && isInstalled(it.archive) }
                .flatMap { spec ->
                    config.threadCounts.map { threads ->
                        PlannedRun.Neural(spec, threads, saveSample = threads == sampleThreads)
                    }
                }
            neural + PlannedRun.System(lang)
        }
    }
}
