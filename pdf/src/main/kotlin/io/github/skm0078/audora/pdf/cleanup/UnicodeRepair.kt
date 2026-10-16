// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/UnicodeRepair.kt
// Purpose: NFC with offsets kept, and the ASCII "|" typed for a danda turned into one (P3-02, ADR-028).

package io.github.skm0078.audora.pdf.cleanup

import java.text.Normalizer

/**
 * Two repairs on [MappedText] that keep every unit's extracted-text offset (ADR-020):
 * - [compose]: NFC. PDFs and OCR give the same letter in different forms ("ো" as one code or as "ে" + "া";
 *   "क़" composed or not); after NFC one text reads, fingerprints and compares the same whatever produced it.
 *   NFC leaves ZWJ and ZWNJ alone. Note that NFC decomposes the nukta letters Unicode excludes from composition
 *   (क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़, ড় ঢ় য়): that is their normal form, and how every NFC text spells them.
 * - [dandasFromPipes]: Hindi and Bengali typed in Word often use "|" for the danda ("।") and "||" for the double
 *   danda. After a Devanagari or Bengali letter they become dandas, so sentences split there and a voice never
 *   reads "vertical bar".
 */
internal object UnicodeRepair {
    /**
     * NFC one cluster at a time: a character with the combining marks after it. A cluster whose form changes maps
     * every unit of its new form to the cluster's first source, so glyph boxes still cover the whole cluster.
     */
    fun compose(input: MappedText): MappedText {
        val text = input.text
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) return input
        val out = MappedText.Builder(text.length)
        var start = 0
        while (start < text.length) {
            val end = clusterEnd(text, start)
            val cluster = text.substring(start, end)
            val composed = Normalizer.normalize(cluster, Normalizer.Form.NFC)
            if (composed.length == cluster.length) {
                composed.forEachIndexed { k, c -> out.append(c, input.sources[start + k]) }
            } else {
                composed.forEach { out.append(it, input.sources[start]) }
            }
            start = end
        }
        return out.build()
    }

    fun dandasFromPipes(input: MappedText): MappedText {
        val text = input.text
        if (PIPE !in text) return input
        val out = MappedText.Builder(text.length)
        var i = 0
        while (i < text.length) {
            val double = text.startsWith(DOUBLE_PIPE, i)
            val width = if (double) DOUBLE_PIPE.length else 1
            if (text[i] == PIPE && isDanda(text, i, i + width)) {
                out.append(if (double) DOUBLE_DANDA else DANDA, input.sources[i])
                i += width
            } else {
                out.append(text[i], input.sources[i])
                i++
            }
        }
        return out.build()
    }

    /** End of the cluster starting at [start]: its first code point and every combining mark after it. */
    private fun clusterEnd(text: String, start: Int): Int {
        var end = start + Character.charCount(text.codePointAt(start))
        while (end < text.length) {
            val cp = text.codePointAt(end)
            if (Character.getType(cp) !in MARK_TYPES) break
            end += Character.charCount(cp)
        }
        return end
    }

    /** After an Indic letter, mark or digit (one space allowed), and before a space, the end or more Indic text. */
    private fun isDanda(text: String, start: Int, end: Int): Boolean {
        var before = start - 1
        if (before >= 0 && text[before] == ' ') before--
        val afterIndic = before >= 0 && isIndic(text.codePointBefore(before + 1))
        val beforeBreak = end == text.length ||
            text[end].isWhitespace() || text[end] in CLOSERS || isIndic(text.codePointAt(end))
        return afterIndic && beforeBreak
    }

    private fun isIndic(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) in INDIC_SCRIPTS

    private const val PIPE = '|'
    private const val DOUBLE_PIPE = "||"
    private const val DANDA = '\u0964'
    private const val DOUBLE_DANDA = '\u0965'

    /** Closing quotes and brackets that may follow a danda. */
    private const val CLOSERS = "\"'\u2019\u201D)]"

    private val INDIC_SCRIPTS = setOf(Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI)

    private val MARK_TYPES = setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
    )
}
