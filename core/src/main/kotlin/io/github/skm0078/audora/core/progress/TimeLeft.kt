// File: core/src/main/kotlin/io/github/skm0078/audora/core/progress/TimeLeft.kt
// Purpose: Estimates listening time left in a book or chapter at the listener's speed and pace (plan item 24).

package io.github.skm0078.audora.core.progress

import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.config.TimeLeftConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle

/**
 * Each sentence takes its characters at the language's speaking rate ([TimeLeftConfig.charsPerSecond]) divided by the
 * speed, plus the silence after it at the listener's pace ([SpeechStyleConfig.silenceAfterMs], as heard once the
 * player's stretch is applied). Pages whose text is known ([pages]) are summed; the others count as their average, so
 * the estimate sharpens as more pages are seen. Pure; cheap enough to run on every position change.
 */
class TimeLeft(
    private val config: TimeLeftConfig = TimeLeftConfig(),
    private val speech: SpeechStyleConfig = SpeechStyleConfig(),
) {
    /**
     * Milliseconds from [charStart] on [page] to the start of [endPage] (exclusive), or null when no page's text is
     * known yet. [pages] maps a page to its units; an empty list (a page without text) counts as silence.
     */
    fun msUntil(
        page: Int,
        charStart: Int,
        endPage: Int,
        pages: Map<Int, List<ReadingUnit>>,
        style: SpeechStyle,
    ): Long? {
        val known = pages.filterValues { it.isNotEmpty() }
        if (known.isEmpty()) return null
        val average = known.values.sumOf { units -> pageMs(units, style) } / known.size
        var total = 0.0
        for (p in page until endPage) {
            val units = pages[p]
            total += when {
                units == null -> average
                p == page -> pageMs(units.filter { it.charEnd > charStart }, style)
                else -> pageMs(units, style)
            }
        }
        return total.toLong()
    }

    /**
     * A rough figure for [pages] pages of a book whose text is not known yet: typical pages in [lang] at [rate],
     * without the pauses. Null for no pages.
     */
    fun roughMs(pages: Int, lang: Lang, rate: Float): Long? {
        if (pages <= 0) return null
        val chars = pages.toDouble() * config.typicalCharsPerPage
        return (chars / config.charsPerSecond.getValue(lang) / speech.clampRate(rate) * MS_PER_SECOND).toLong()
    }

    private fun pageMs(units: List<ReadingUnit>, style: SpeechStyle): Double = units.sumOf { unitMs(it, style) }

    private fun unitMs(unit: ReadingUnit, style: SpeechStyle): Double {
        val rate = speech.clampRate(style.rate)
        val speaking = unit.text.length / config.charsPerSecond.getValue(unit.lang) / rate * MS_PER_SECOND
        val silence = speech.silenceAfterMs(unit.pauseAfterMs, style) / speech.playerSpeed(style)
        return speaking + silence
    }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
    }
}
