// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/TextNormalizer.kt
// Purpose: Contract for turning a unit's written text into what a voice should say, applied right before synthesis.

package io.github.skm0078.audora.speech.normalize

/**
 * What a piece of text is on the page. Some readings depend on it: "IV. Results" is "four" only in a heading.
 * Playback passes [HEADING] for sentences layout found in a heading (`UnitKind.HEADING`), [EQUATION] for a display
 * equation read as written (its maths and logic symbols become words, RI-7b), [BODY] for the rest.
 */
enum class TextRole { BODY, HEADING, EQUATION }

/**
 * Rewrites written text into speakable words (numbers, dates, currency, abbreviations, links). Runs at render time,
 * never on `ReadingUnit.text`, which stays the extracted text that the reader shows and resume anchors hash
 * (ADR-019). Implementations are pure, thread-safe and never throw on any input string.
 */
fun interface TextNormalizer {
    fun normalize(text: String, role: TextRole): String
}

/** One ordered rewrite step of a normalizer. */
internal fun interface NormalizationRule {
    fun apply(text: String, role: TextRole): String
}

/**
 * Replacement for an abbreviation whose final full stop may also end the sentence: "… and so on etc." keeps a
 * full stop after "et cetera" so the voice still falls at the end. [match] is a match found in [text].
 */
internal fun keepSentenceStop(text: String, match: MatchResult, spoken: String): String {
    val endsSentence = match.value.endsWith('.') && text.substring(match.range.last + 1).isBlank()
    return if (endsSentence) "$spoken." else spoken
}
