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
 * read confidently does not carry a line. Least confident lines first, at most `retryMaxLines`, within `retryBudgetMs`.
 */
internal class OcrLineRetry(
    private val engine: OcrEngine,
    private val config: OcrConfig,
    private val logContext: String,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun improve(page: RecognizedPage, image: PageImage, langs: Set<Lang>): RecognizedPage {
        val others = (engine.installedLangs() - langs).map { setOf(it) }
        val enabled = config.retryLineBelow > 0 && config.retryMaxLines > 0 && others.isNotEmpty()
        val unsure = if (!enabled) {
            emptyList()
        } else {
            page.lines.indices
                .filter { confidenceOf(page.lines[it].words) < config.retryLineBelow }
                .sortedBy { confidenceOf(page.lines[it].words) }
                .take(config.retryMaxLines)
        }
        if (unsure.isEmpty()) return page
        val started = nanoTime()
        val deadline = started + config.retryBudgetMs * NANOS_PER_MS
        val lines = page.lines.toMutableList()
        var replaced = 0
        for (index in unsure) {
            if (nanoTime() >= deadline) break
            reread(lines[index], image, others)?.let {
                lines[index] = it
                replaced++
            }
        }
        AudoraLog.i(
            TAG,
            "OCR line retry $logContext page=${image.pageIndex} langs=$langs tried=${unsure.size} " +
                "replaced=$replaced ms=${(nanoTime() - started) / NANOS_PER_MS}",
        )
        return if (replaced == 0) page else RecognizedPage(lines, page.width, page.height)
    }

    /** The line read alone with the model that reads it most confidently, or null when none clearly beats it. */
    private fun reread(line: RecognizedLine, image: PageImage, others: List<Set<Lang>>): RecognizedLine? {
        val top = line.words.minOf { it.top }
        val bottom = line.words.maxOf { it.bottom }
        val pad = (bottom - top) / 2
        val left = line.words.minOf { it.left } - pad
        val upper = top - pad
        val crop = image.crop(left, upper, line.words.maxOf { it.right } + pad, bottom + pad) ?: return null
        var best: List<RecognizedWord>? = null
        // A replacement must read as a clean line itself, not only less badly ("AYE |" for a Bengali word).
        var bestConfidence = maxOf(confidenceOf(line.words) + config.retryMinGain, config.retryLineBelow)
        for (langs in others) {
            val read = engine.recognize(crop, langs) as? Outcome.Success ?: continue
            val words = read.value.lines.flatMap { it.words }
            val confidence = confidenceOf(words)
            if (words.isNotEmpty() && confidence >= bestConfidence) {
                best = words
                bestConfidence = confidence
            }
        }
        val x = left.coerceAtLeast(0)
        val y = upper.coerceAtLeast(0)
        return best?.let { words ->
            RecognizedLine(
                words.map { RecognizedWord(it.text, it.left + x, it.top + y, it.right + x, it.bottom + y, it.conf) },
                line.paragraph,
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
