// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/ScriptRouter.kt
// Purpose: Cuts one sentence into script runs so each part is read by its own language's voice (P3-01, ADR-026).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ScriptLang

/** One part of a sentence and the language whose voice reads it. [text] is trimmed and never blank. */
data class ScriptSegment(val text: String, val lang: Lang)

/**
 * Rules (Build Spec §3, ADR-005, ADR-026):
 * - Each letter's script picks its language ([ScriptLang]); spaces, punctuation and ASCII digits go with the run
 *   before them, so "world, " keeps its comma. A cut falls after the last space before the new script, so an
 *   opening quote or bracket travels with the words it opens.
 * - In a mostly Hindi or Bengali sentence, an English run shorter than
 *   [ScriptRoutingConfig.minEnglishWordsInIndic] words stays with the Indic voice next to it.
 * - Indic text inside an English sentence always gets its own voice, however short.
 * - Text with no letter at all is one segment in [route]'s fallback language.
 *
 * Pure and deterministic; segments come back in reading order, adjacent ones in different languages.
 */
class ScriptRouter(private val config: ScriptRoutingConfig = ScriptRoutingConfig()) {
    fun route(text: String, fallback: Lang): List<ScriptSegment> {
        val runs = scriptRuns(text)
        if (runs.isEmpty()) return if (text.isBlank()) emptyList() else listOf(ScriptSegment(text.trim(), fallback))
        val voiced = keepShortEnglishWithIndic(text, runs)
        return merge(voiced).map { ScriptSegment(text.substring(it.start, it.end).trim(), it.lang) }
    }

    private class Run(val start: Int, var end: Int, var lang: Lang, var letters: Int)

    /** Maximal runs of one script; the first starts at 0 and the last ends at the text's end. */
    private fun scriptRuns(text: String): List<Run> {
        val runs = ArrayList<Run>()
        var lastLetterEnd = 0
        var lastSpaceEnd = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val next = i + Character.charCount(cp)
            val lang = ScriptLang.of(cp)
            val current = runs.lastOrNull()
            when {
                lang == null -> if (Character.isWhitespace(cp)) lastSpaceEnd = next

                current == null -> runs.add(Run(0, text.length, lang, 0))

                current.lang != lang -> {
                    val cut = if (lastSpaceEnd > lastLetterEnd) lastSpaceEnd else i
                    current.end = cut
                    runs.add(Run(cut, text.length, lang, 0))
                }
            }
            if (lang != null) {
                runs.last().letters++
                lastLetterEnd = next
            }
            i = next
        }
        return runs
    }

    private fun keepShortEnglishWithIndic(text: String, runs: List<Run>): List<Run> {
        val indicLetters = runs.filter { it.lang != Lang.EN }.sumOf { it.letters }
        val englishLetters = runs.filter { it.lang == Lang.EN }.sumOf { it.letters }
        // Same tie rule as the unit's own language (ScriptDetector): a tie counts as Indic.
        if (indicLetters == 0 || englishLetters > indicLetters) return runs
        runs.forEachIndexed { i, run ->
            if (run.lang == Lang.EN && words(text, run) < config.minEnglishWordsInIndic) {
                val neighbour = runs.getOrNull(i - 1)?.takeIf { it.lang != Lang.EN } ?: runs.getOrNull(i + 1)
                if (neighbour != null && neighbour.lang != Lang.EN) run.lang = neighbour.lang
            }
        }
        return runs
    }

    /** Whitespace-separated tokens holding a letter of [run]'s script: "U.N." is one word, "12" none. */
    private fun words(text: String, run: Run): Int = text.substring(run.start, run.end)
        .split(WHITESPACE)
        .count { token -> token.codePoints().anyMatch { ScriptLang.of(it) == run.lang } }

    private fun merge(runs: List<Run>): List<Run> {
        val merged = ArrayList<Run>()
        for (run in runs) {
            val last = merged.lastOrNull()
            if (last != null && last.lang == run.lang) {
                last.end = run.end
                last.letters += run.letters
            } else {
                merged.add(Run(run.start, run.end, run.lang, run.letters))
            }
        }
        return merged
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
