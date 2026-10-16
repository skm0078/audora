// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/IndicReorder.kt
// Purpose: Puts visual-order Hindi and Bengali text back in Unicode (logical) order (Build Spec §5 step 3b).

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.pdf.cleanup.MappedText

/** Which parts of a page's Indic text are in visual order and need [IndicReorder]. */
data class IndicOrder(val prebaseVisual: Boolean, val rephVisual: Boolean) {
    val isLogical: Boolean get() = !prebaseVisual && !rephVisual

    companion object {
        val LOGICAL = IndicOrder(prebaseVisual = false, rephVisual = false)
    }
}

/**
 * What a page's text says about its order. Unicode (logical) text never starts a word with a pre-base vowel sign
 * (ि ি ে ৈ) and never ends a syllable with a reph (ra + virama with no consonant after it); text extracted in
 * visual (drawing) order does both, often. Kept per page and summed over neighbours, so a page with too few
 * signs to tell follows the pages around it.
 *
 * @property prebaseSigns pre-base vowel signs on the page.
 * @property wordInitialPrebase those that start a word before a consonant.
 * @property rephs ra + virama pairs that follow a consonant syllable.
 * @property rephsAtSyllableEnd those not followed by a consonant.
 */
data class IndicOrderEvidence(
    val prebaseSigns: Int,
    val wordInitialPrebase: Int,
    val rephs: Int,
    val rephsAtSyllableEnd: Int,
) {
    operator fun plus(other: IndicOrderEvidence) = IndicOrderEvidence(
        prebaseSigns + other.prebaseSigns,
        wordInitialPrebase + other.wordInitialPrebase,
        rephs + other.rephs,
        rephsAtSyllableEnd + other.rephsAtSyllableEnd,
    )

    /** True or false when there are enough pre-base signs to tell; null when there are not. */
    fun prebaseVerdict(config: QualityConfig): Boolean? = when {
        prebaseSigns < config.minVisualOrderSigns -> null
        else -> wordInitialPrebase >= config.minWordInitialShare * prebaseSigns
    }

    fun rephVerdict(config: QualityConfig): Boolean? = when {
        rephs < config.minRephs -> null
        else -> rephsAtSyllableEnd >= config.minRephAtSyllableEndShare * rephs
    }

    companion object {
        val NONE = IndicOrderEvidence(0, 0, 0, 0)

        fun of(text: CharSequence): IndicOrderEvidence {
            var prebase = 0
            var initial = 0
            var rephs = 0
            var rephsAtEnd = 0
            for (i in text.indices) {
                val c = text[i]
                if (IndicChars.classOf(c) == IndicClass.PREBASE_VOWEL) {
                    prebase++
                    val startsWord = i == 0 || !IndicChars.isIndicLetter(text[i - 1])
                    if (startsWord && i + 1 < text.length && IndicChars.isConsonant(text[i + 1])) initial++
                } else if (isRephAfterSyllable(text, i)) {
                    rephs++
                    if (i + 2 >= text.length || !IndicChars.isConsonant(text[i + 2])) rephsAtEnd++
                }
            }
            return IndicOrderEvidence(prebase, initial, rephs, rephsAtEnd)
        }

        /** Ra + virama at [i] right after a consonant or a vowel sign (not after a virama: that is a conjunct). */
        private fun isRephAfterSyllable(text: CharSequence, i: Int): Boolean =
            i > 0 && i + 1 < text.length && IndicChars.isRa(text[i]) && IndicChars.isVirama(text[i + 1]) &&
                IndicChars.classOf(text[i - 1]) in SYLLABLE_ENDS

        private val SYLLABLE_ENDS =
            setOf(IndicClass.CONSONANT, IndicClass.NUKTA, IndicClass.VOWEL_SIGN, IndicClass.PREBASE_VOWEL)
    }
}

/**
 * Decides a page's [IndicOrder]: from its own evidence when it has enough, otherwise from the sum over the page
 * and its neighbours, otherwise logical (the text is left as extracted). Rephs are only reordered on a page whose
 * pre-base signs are in visual order too: a logical-order page never needs it.
 */
internal fun indicOrderOf(
    own: IndicOrderEvidence,
    neighbours: List<IndicOrderEvidence>,
    config: QualityConfig,
): IndicOrder {
    val pooled by lazy { neighbours.fold(own, IndicOrderEvidence::plus) }
    val prebase = own.prebaseVerdict(config) ?: pooled.prebaseVerdict(config) ?: false
    if (!prebase) return IndicOrder.LOGICAL
    val reph = own.rephVerdict(config) ?: pooled.rephVerdict(config) ?: false
    return IndicOrder(prebaseVisual = true, rephVisual = reph)
}

/**
 * Step 3b of Build Spec §5 for Hindi and Bengali, on one paragraph:
 * 1. **Placeholder glyphs** inside a cluster go: some fonts draw part of a vowel sign as a glyph with no Unicode
 *    value, which arrives as U+FFFD between a consonant and its sign ("ক\uFFFDো"). Every page.
 * 2. **Visual order** ([IndicOrder]). Old Office exports and some PDF printers write Indic text in the order glyphs
 *    are drawn: the pre-base vowel sign before its consonant cluster ("िकताब" for "किताब", Bengali "েবাঝা" for
 *    "বোঝা") and the reph after the syllable it sits on ("ধমর্" for "ধর্ম"). On such pages every pre-base vowel
 *    sign before a consonant cluster moves after the cluster, the Bengali two-part vowels the move leaves split are
 *    composed (ে + া → ো, ে + ৗ → ৌ), and every reph moves before the consonant cluster of its syllable.
 *
 * Applied to logical-order text step 2 would scramble it, which is why it runs only where [indicOrderOf] says so. It
 * fixes order only: glyphs a font maps to the wrong letters stay wrong; the OCR cross-check catches those
 * (ADR-011).
 *
 * Characters move only within a syllable, and each keeps the source offset of the position it lands on, so offsets
 * stay ascending (ADR-018) and every box is still a box of the same cluster.
 */
internal object IndicReorder {
    private const val PLACEHOLDER = '\uFFFD'

    fun apply(input: MappedText, order: IndicOrder): MappedText {
        val chars = input.text.toCharArray()
        val keep = BooleanArray(chars.size) { true }
        val placeholders = dropPlaceholders(chars, keep)
        if (order.isLogical && placeholders == 0) return input
        if (order.prebaseVisual) movePrebaseSigns(chars, keep)
        if (order.rephVisual) moveRephs(chars, keep)
        val out = MappedText.Builder(chars.size)
        for (i in chars.indices) if (keep[i]) out.append(chars[i], input.sources[i])
        return out.build()
    }

    /** Marks U+FFFD between an Indic letter and a vowel sign, virama or nukta as dropped; returns how many. */
    private fun dropPlaceholders(chars: CharArray, keep: BooleanArray): Int {
        var dropped = 0
        for (i in 1 until chars.size - 1) {
            if (chars[i] != PLACEHOLDER || !IndicChars.isIndicLetter(chars[i - 1])) continue
            val next = IndicChars.classOf(chars[i + 1])
            if (next == IndicClass.VOWEL_SIGN || next == IndicClass.VIRAMA || next == IndicClass.NUKTA) {
                keep[i] = false
                dropped++
            }
        }
        return dropped
    }

    private fun movePrebaseSigns(chars: CharArray, keep: BooleanArray) {
        var i = 0
        while (i < chars.size) {
            val end = if (IndicChars.classOf(chars[i]) == IndicClass.PREBASE_VOWEL) clusterEnd(chars, i + 1) else -1
            if (end < 0) {
                i++
                continue
            }
            val sign = chars[i]
            // The cluster moves left by one and the sign lands after it.
            System.arraycopy(chars, i + 1, chars, i, end - i - 1)
            chars[end - 1] = sign
            composeTwoPartVowel(chars, keep, end - 1)
            i = end
        }
    }

    /** End (exclusive) of the consonant cluster at [start]: C nukta? (virama C nukta?)*; -1 when none starts there. */
    private fun clusterEnd(chars: CharArray, start: Int): Int {
        if (start >= chars.size || !IndicChars.isConsonant(chars[start])) return -1
        var end = afterNukta(chars, start + 1)
        while (end + 1 < chars.size && IndicChars.isVirama(chars[end]) && IndicChars.isConsonant(chars[end + 1])) {
            end = afterNukta(chars, end + 2)
        }
        return end
    }

    private fun afterNukta(chars: CharArray, at: Int): Int =
        if (at < chars.size && IndicChars.classOf(chars[at]) == IndicClass.NUKTA) at + 1 else at

    /** ে at [at] followed by া or ৗ becomes ো or ৌ; the second part is dropped with its source offset. */
    private fun composeTwoPartVowel(chars: CharArray, keep: BooleanArray, at: Int) {
        if (chars[at] != IndicChars.BENGALI_E || at + 1 >= chars.size) return
        chars[at] = when (chars[at + 1]) {
            IndicChars.BENGALI_AA -> IndicChars.BENGALI_O
            IndicChars.BENGALI_AU_LENGTH -> IndicChars.BENGALI_AU
            else -> return
        }
        keep[at + 1] = false
    }

    private fun moveRephs(chars: CharArray, keep: BooleanArray) {
        var i = 1
        while (i + 1 < chars.size) {
            val reph = keep[i] && IndicChars.isRa(chars[i]) && IndicChars.isVirama(chars[i + 1])
            val start = if (reph) syllableStart(chars, keep, i) else -1
            if (start >= 0) {
                val ra = chars[i]
                val virama = chars[i + 1]
                System.arraycopy(chars, start, chars, start + 2, i - start)
                System.arraycopy(keep, start, keep, start + 2, i - start)
                chars[start] = ra
                chars[start + 1] = virama
                keep[start] = true
                keep[start + 1] = true
            }
            i += if (reph) 2 else 1
        }
    }

    /**
     * Start of the consonant cluster of the syllable just before [end]: back over vowel signs and modifiers, then
     * a consonant (with nukta), then virama-joined consonants. -1 when no consonant is there (a word-initial reph,
     * or a conjunct: ra + virama right after a virama is a cluster, not a reph). Dropped characters are skipped.
     */
    private fun syllableStart(chars: CharArray, keep: BooleanArray, end: Int): Int {
        var k = end - 1
        while (k >= 0 && (!keep[k] || IndicChars.classOf(chars[k]).followsBase())) k--
        if (k >= 0 && IndicChars.classOf(chars[k]) == IndicClass.NUKTA) k--
        if (k < 0 || !IndicChars.isConsonant(chars[k])) return -1
        while (k >= 2 && IndicChars.isVirama(chars[k - 1]) && IndicChars.isConsonant(chars[k - 2])) k -= 2
        return k
    }

    private fun IndicClass.followsBase() =
        this == IndicClass.VOWEL_SIGN || this == IndicClass.PREBASE_VOWEL || this == IndicClass.MODIFIER
}
