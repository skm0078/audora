// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/legacy/LegacyRuns.kt
// Purpose: Finds the stretches of a page's extracted text set in a legacy Hindi or Bengali font (P3-04).

package io.github.skm0078.audora.pdf.legacy

import io.github.skm0078.audora.core.config.LegacyEncoding
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.pdf.extract.PageText

/** [start] inclusive to [end] exclusive in `PageText.text`, set in a font of [encoding]. */
internal data class LegacyRun(val start: Int, val end: Int, val encoding: LegacyEncoding)

/**
 * A page's legacy-font runs, by offset into its extracted text. A run counts when its font's name contains one of
 * [QualityConfig.legacyFonts] (subset prefix ignored) and at least [QualityConfig.minLegacyLatinShare] of its
 * letters are Latin codes: a Unicode font that kept a legacy name (ChanakyaUni) is read as is.
 */
class LegacyRuns internal constructor(
    internal val runs: List<LegacyRun>,
    /** Letters inside the runs, before conversion. */
    val letters: Int,
) {
    val isEmpty: Boolean get() = runs.isEmpty()

    /** The encoding at extracted-text offset [offset], or null when it is not in a legacy run. */
    internal fun encodingAt(offset: Int): LegacyEncoding? {
        val index = runs.binarySearch { run ->
            when {
                offset < run.start -> 1
                offset >= run.end -> -1
                else -> 0
            }
        }
        return if (index >= 0) runs[index].encoding else null
    }

    companion object {
        val NONE: LegacyRuns = LegacyRuns(emptyList(), 0)

        /** Latin letters, Latin-1 and Latin Extended-A/B: where Windows-1252 codes such as š, Ÿ and ƒ land. */
        private const val LATIN_END = 0x0250

        fun of(page: PageText, config: QualityConfig): LegacyRuns {
            var letters = 0
            val runs = page.fontRuns.mapNotNull { run ->
                val encoding = encodingOf(run.fontName, config) ?: return@mapNotNull null
                var runLetters = 0
                var latin = 0
                for (i in run.start until run.end) {
                    val c = page.text[i]
                    if (!c.isLetter()) continue
                    runLetters++
                    if (c.code < LATIN_END) latin++
                }
                // A run of digits or punctuation alone (one glyph in a word) has nothing to judge by: it is converted.
                if (latin < config.minLegacyLatinShare * runLetters) return@mapNotNull null
                letters += runLetters
                LegacyRun(run.start, run.end, encoding)
            }
            if (runs.isEmpty()) return NONE
            return LegacyRuns(runs.sortedBy { it.start }, letters)
        }

        internal fun encodingOf(fontName: String, config: QualityConfig): LegacyEncoding? {
            val name = fontName.substringAfter('+').lowercase()
            return config.legacyFonts.entries.firstOrNull { name.contains(it.key) }?.value
        }
    }
}
