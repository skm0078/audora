// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/SentenceFingerprint.kt
// Purpose: Stable 64-bit hashes of a sentence's first and last characters, blind to spacing, case and punctuation.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.config.ResumeConfig
import java.text.Normalizer
import java.util.Locale

/**
 * Hashes only what a better pipeline is unlikely to change: letters, digits and combining marks (Devanagari and
 * Bengali vowel signs are marks, so they count), after NFKC (ligatures like "ﬁ" become "fi") and lower-casing.
 * Spaces, hyphens, punctuation, soft hyphens and zero-width characters are dropped, so de-hyphenation, ligature
 * expansion and whitespace fixes (P2-03) keep a sentence's fingerprints.
 *
 * Fingerprint the sentence as extracted, not as rewritten for speech: speech normalization (P2-04) turns "₹5"
 * into words and would change every fingerprint with each normalizer fix.
 * TODO(P2-04): keep the extracted text on ReadingUnit when `text` becomes the speech-normalized form.
 *
 * The hash is FNV-1a 64 over UTF-8, fixed here forever: anchors saved by one app version are read by the next.
 */
class SentenceFingerprint(private val config: ResumeConfig = ResumeConfig()) {
    /** Fingerprint of the sentence's first [ResumeConfig.fingerprintChars] normalized characters. */
    fun head(text: String): Long = hash(normalized(text).take(config.fingerprintChars))

    /** Fingerprint of its last [ResumeConfig.fingerprintChars] normalized characters. */
    fun tail(text: String): Long = hash(normalized(text).takeLast(config.fingerprintChars))

    private fun normalized(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val kept = StringBuilder(folded.length)
        folded.codePoints().filter(::isKept).forEach { kept.appendCodePoint(it) }
        // Code points, not chars, so an emoji or a supplementary-plane letter is never cut in half by take().
        return kept.toString()
    }

    private fun String.take(n: Int): String = substring(0, offsetByCodePoints(0, minOf(n, codePointCount(0, length))))

    private fun String.takeLast(n: Int): String {
        val count = codePointCount(0, length)
        return substring(offsetByCodePoints(0, count - minOf(n, count)))
    }

    companion object {
        /** A sentence with nothing to hash (only punctuation or symbols). Never counts as a match. */
        const val NONE: Long = 0L

        private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
        private const val BYTE_MASK = 0xff

        private fun isKept(codePoint: Int): Boolean = Character.isLetterOrDigit(codePoint) ||
            when (Character.getType(codePoint).toByte()) {
                Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
                else -> false
            }

        private fun hash(normalized: String): Long {
            if (normalized.isEmpty()) return NONE
            var hash = FNV_OFFSET_BASIS
            for (byte in normalized.toByteArray(Charsets.UTF_8)) {
                hash = (hash xor (byte.toInt() and BYTE_MASK).toLong()) * FNV_PRIME
            }
            // A real hash of 0 would read as NONE; nudging it costs one value of 2^64.
            return if (hash == NONE) 1L else hash
        }
    }
}
