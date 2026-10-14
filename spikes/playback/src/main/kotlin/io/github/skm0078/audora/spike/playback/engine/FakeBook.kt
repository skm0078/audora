// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/engine/FakeBook.kt
// Purpose: A deterministic stand-in for an extracted PDF: numbered sentences with pages and spoken lengths.

package io.github.skm0078.audora.spike.playback.engine

import io.github.skm0078.audora.spike.playback.config.SpikeConfig
import java.util.Random

/** One speakable unit; the real app's ReadingUnit reduced to what playback needs. */
data class FakeUnit(val index: Int, val page: Int, val durationMs: Long)

class FakeBook(private val config: SpikeConfig) {
    val unitCount: Int get() = config.unitCount

    /** Same index and seed always give the same unit, so runs on two phones are comparable. */
    fun unit(index: Int): FakeUnit {
        require(index in 0 until unitCount) { "Unit $index is outside the book (0 until $unitCount)" }
        val span = config.maxUnitMs - config.minUnitMs
        val random = Random(config.seed * SEED_MIX + index)
        val durationMs = config.minUnitMs + (random.nextDouble() * span).toLong()
        return FakeUnit(index = index, page = index / config.unitsPerPage, durationMs = durationMs)
    }

    fun pageCount(): Int = (unitCount + config.unitsPerPage - 1) / config.unitsPerPage

    /** Unit indexes on [page], clipped to the end of the book. */
    fun unitsOnPage(page: Int): IntRange {
        val first = page * config.unitsPerPage
        val last = minOf(first + config.unitsPerPage, unitCount) - 1
        return first..last
    }

    private companion object {
        /** Large odd multiplier so neighbouring seeds do not produce overlapping sequences. */
        const val SEED_MIX = 1_000_003L
    }
}
