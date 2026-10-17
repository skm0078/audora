// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/WordModels.kt
// Purpose: Per-language word lists and the letter-trigram models trained from them, for the speakability score.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.math.ln

/** The language models the speakability score reads; null for a language without one (the scorer abstains). */
fun interface WordModels {
    fun of(lang: Lang): LanguageModel?
}

/**
 * One language's words, most frequent first, with their Zipf frequency × 10 (RI-3 ranks corrections by it), and a
 * letter-trigram model trained on the same words (each word once: the model learns how words are spelled, not which
 * ones are common).
 *
 * [letterScore] is calibrated by the model itself, so no per-language constant is needed: 1 is how likely the
 * model's own words are on average, 0 is a uniformly random string over the letters it knows.
 */
class LanguageModel(val words: Map<String, Int>) {
    private val trigrams = HashMap<Long, Int>()
    private val contexts = HashMap<Long, Int>()
    private val alphabet: Int
    private val realLogProb: Double
    private val randomLogProb: Double

    init {
        require(words.isNotEmpty()) { "a language model needs words" }
        val letters = HashSet<Int>()
        for (word in words.keys) {
            val points = padded(word)
            points.forEach { if (it != START && it != END) letters.add(it) }
            for (i in 0..points.size - TRIGRAM) {
                trigrams.merge(key(points[i], points[i + 1], points[i + 2]), 1, Int::plus)
                contexts.merge(key(0, points[i], points[i + 1]), 1, Int::plus)
            }
        }
        // The end marker is a possible next letter too.
        alphabet = letters.size + 1
        realLogProb = words.keys.sumOf { meanLogProb(it) } / words.size
        randomLogProb = ln(1.0 / alphabet)
    }

    operator fun contains(word: String): Boolean = word in words

    /** How word-like [word]'s spelling is, 0–1 (see the class comment); [word] as [Words] gives it. */
    fun letterScore(word: String): Float =
        ((meanLogProb(word) - randomLogProb) / (realLogProb - randomLogProb)).coerceIn(0.0, 1.0).toFloat()

    /** Mean natural-log probability of [word]'s letter trigrams, with add-one smoothing. */
    private fun meanLogProb(word: String): Double {
        val points = padded(word)
        var sum = 0.0
        val count = points.size - TRIGRAM + 1
        for (i in 0 until count) {
            val seen = trigrams[key(points[i], points[i + 1], points[i + 2])] ?: 0
            val context = contexts[key(0, points[i], points[i + 1])] ?: 0
            sum += ln((seen + 1.0) / (context + alphabet))
        }
        return sum / count
    }

    private companion object {
        const val TRIGRAM = 3
        const val START = 1
        const val END = 2
        const val BITS = 21
        const val MASK = (1L shl BITS) - 1

        fun padded(word: String): IntArray {
            val points = word.codePoints().toArray()
            return intArrayOf(START, START) + points + END
        }

        fun key(a: Int, b: Int, c: Int): Long =
            ((a.toLong() and MASK) shl (2 * BITS)) or ((b.toLong() and MASK) shl BITS) or (c.toLong() and MASK)
    }
}

/**
 * The word lists bundled with `:pdf` (`speakability/<lang>.words.gz`, built by scripts/speakability/build_models.py
 * from wordfreq, CC BY-SA 4.0; ADR-035). A language is loaded and its trigram model trained the first time a page in
 * it is scored, then kept. A missing or unreadable list is logged once and that language scores without the
 * dictionary and letter signals.
 */
object BundledWordModels : WordModels {
    private const val TAG = "Speakability"
    private const val HEADER = "# audora-words 1 "
    private const val RESOURCES = "/io/github/skm0078/audora/pdf/speakability"
    private val loaded = ConcurrentHashMap<Lang, Loaded>()

    /** Wraps a nullable model, since ConcurrentHashMap holds no null. */
    private class Loaded(val model: LanguageModel?)

    override fun of(lang: Lang): LanguageModel? = loaded.computeIfAbsent(lang) { Loaded(load(it)) }.model

    private fun load(lang: Lang): LanguageModel? {
        val name = "$RESOURCES/${lang.bcp47Tag}.words.gz"
        val words = try {
            BundledWordModels::class.java.getResourceAsStream(name)?.let { stream ->
                GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).use { parse(it.readLines()) }
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "word list unreadable, lang=$lang", e)
            null
        }
        if (words.isNullOrEmpty()) {
            AudoraLog.w(TAG, "no word list, lang=$lang: scoring without the dictionary and letter signals")
        } else {
            AudoraLog.i(TAG, "word list loaded, lang=$lang words=${words.size}")
        }
        return words?.takeIf { it.isNotEmpty() }?.let(::LanguageModel)
    }

    /** The list's lines after its header; null when the header is not this format. */
    internal fun parse(lines: List<String>): Map<String, Int>? {
        if (lines.firstOrNull()?.startsWith(HEADER) != true) return null
        val words = LinkedHashMap<String, Int>(lines.size)
        for (line in lines.drop(1)) {
            val tab = line.indexOf('\t')
            val zipf = line.substring(tab + 1).toIntOrNull()
            if (tab > 0 && zipf != null) words.putIfAbsent(line.substring(0, tab), zipf)
        }
        return words
    }
}
