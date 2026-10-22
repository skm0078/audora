// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrLineRetry.kt
// Purpose: Reads a page's least confident lines again on their own with the other installed models (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PageImage

/**
 * A page is read in the languages its book uses, so a line in another script reads as noise: an English running
 * header on a Hindi page came out as "#फ08 52 (९5 ८0905". Each line below `OcrConfig.retryLineBelow` is cut out of
 * the page image and read alone with each other installed model; the most confident read replaces it when it gains
 * at least `retryMinGain` and itself reads above `retryLineBelow`. Confidence is weighted by letters, so punctuation
 * read confidently does not carry a line, and a line with no letter at all ("____", "* * *", a rule read as "| |")
 * is never retried: it reads 0 and could only waste the budget. Least confident lines first, at most
 * `retryMaxLines`, within `retryBudgetMs`. Models are the outer loop, so each is loaded once per page (switching the
 * engine's model reloads it), and the deadline is checked before every read.
 */
internal class OcrLineRetry(
    private val engine: OcrEngine,
    private val config: OcrConfig,
    private val logContext: String,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** The models a page read in [langs] is retried with: what [improve] depends on besides the page. */
    fun otherModels(langs: Set<Lang>): List<Lang> = (engine.installedLangs() - langs).sortedBy { it.ordinal }

    fun improve(page: RecognizedPage, image: PageImage, langs: Set<Lang>): RecognizedPage {
        val others = otherModels(langs)
        val enabled = config.retryLineBelow > 0 && config.retryMaxLines > 0 && others.isNotEmpty()
        val unsure = if (!enabled) {
            emptyList()
        } else {
            page.lines.indices
                .filter { i -> page.lines[i].words.any { lettersOf(it) > 0 } }
                .filter { confidenceOf(page.lines[it].words) < config.retryLineBelow }
                .sortedBy { confidenceOf(page.lines[it].words) }
                .take(config.retryMaxLines)
        }
        if (unsure.isEmpty()) return page
        val started = nanoTime()
        val best = reread(unsure.map { Candidate(page.lines[it], it, image) }, others, started)
        val lines = page.lines.toMutableList()
        best.forEach { (index, line) -> lines[index] = line }
        AudoraLog.i(
            TAG,
            "OCR line retry $logContext page=${image.pageIndex} langs=$langs tried=${unsure.size} " +
                "replaced=${best.size} ms=${(nanoTime() - started) / NANOS_PER_MS}",
        )
        return if (best.isEmpty()) page else RecognizedPage(lines, page.width, page.height)
    }

    /** Each candidate's best read, by line index, trying every model in turn until the deadline. */
    private fun reread(candidates: List<Candidate>, others: List<Lang>, started: Long): Map<Int, RecognizedLine> {
        val deadline = started + config.retryBudgetMs * NANOS_PER_MS
        val best = HashMap<Int, RecognizedLine>()
        for (model in others) {
            for (candidate in candidates) {
                if (nanoTime() >= deadline) return best
                candidate.read(model)?.let { best[candidate.index] = it }
            }
        }
        return best
    }

    /** One unsure line, cut out of the page with half its height around it, and the best read of it so far. */
    private inner class Candidate(private val line: RecognizedLine, val index: Int, image: PageImage) {
        private val pad = (line.words.maxOf { it.bottom } - line.words.minOf { it.top }) / 2
        private val left = line.words.minOf { it.left } - pad
        private val upper = line.words.minOf { it.top } - pad
        private val crop = image.crop(
            left,
            upper,
            line.words.maxOf { it.right } + pad,
            line.words.maxOf { it.bottom } + pad,
        )

        // A replacement must read as a clean line itself, not only less badly ("AYE |" for a Bengali word).
        private var bestConfidence = maxOf(confidenceOf(line.words) + config.retryMinGain, config.retryLineBelow)

        /** The line read with [model], placed back on the page, when it beats the best read so far; else null. */
        fun read(model: Lang): RecognizedLine? {
            val words = crop?.let { engine.recognize(it, setOf(model)) as? Outcome.Success }
                ?.value?.lines?.flatMap { it.words }
                ?.takeIf { it.isNotEmpty() && confidenceOf(it) >= bestConfidence }
                ?: return null
            bestConfidence = confidenceOf(words)
            val x = left.coerceAtLeast(0)
            val y = upper.coerceAtLeast(0)
            return RecognizedLine(
                words.map { RecognizedWord(it.text, it.left + x, it.top + y, it.right + x, it.bottom + y, it.conf) },
                line.paragraph,
                line.size,
            )
        }
    }

    private companion object {
        const val TAG = "Audora.Ocr"
        const val NANOS_PER_MS = 1_000_000L

        /** Confidence weighted by each word's letters and digits: a stray "|" read at 95 says nothing. */
        fun confidenceOf(words: List<RecognizedWord>): Int {
            val chars = words.sumOf { lettersOf(it) }
            return if (chars == 0) 0 else (words.sumOf { it.conf.toLong() * lettersOf(it) } / chars).toInt()
        }

        fun lettersOf(word: RecognizedWord): Int = word.text.count { c ->
            Character.isLetterOrDigit(c) || Character.getType(c).let {
                it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
            }
        }
    }
}
