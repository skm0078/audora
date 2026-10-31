// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ShareCardConfig.kt
// Purpose: Tunables for sharing lines as a picture (quote cards, ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.core.config

/**
 * A quote card holds a short, credited quote: Kindle, Play Books and Goodreads share the same, and keeping it short
 * keeps it on the fair-dealing side (the plan's guard rails).
 *
 * @property maxWords a longer selection is cut to this many words, at a sentence end when one is close, else with "…".
 * @property sentenceEndShare a sentence end counts as close when it keeps at least this share of [maxWords].
 * @property maxDedicationChars the optional "For Ma" line under the credit.
 * @property keptCards cards kept in the share folder; older ones are deleted when a new one is written.
 * @property snippetMaxShare a boxed page snippet covers at most this share of the page's area (a third: a passage with
 *   its look). The whole page is a separate choice the listener makes on the card (ADR 2026-10-30-whole-page-cards).
 * @property snippetMinSide a snippet's side is never narrower than this share of the page's, so a handle stays
 *   grabbable and the picture readable.
 * @property snippetPad the box first drawn around a sentence leaves this share of the page around its lines.
 * @property snippetNudgeStep one TalkBack action ("Move up", "Make bigger") moves or grows the box by this share of
 *   the page: small enough to aim, big enough that a few steps cross the page.
 * @property maxTextWords lines shared as text are cut to this many words, at a sentence end when one is close: longer
 *   than a card's quote (text has no layout to fill), still a passage and never a page (the same guard).
 * @property maxPageWords a whole page on a card ("Whole page" on the card screen) is cut to this many words: more
 *   than a dense page's worth would be too small to read even on a story.
 */
data class ShareCardConfig(
    val maxWords: Int = 60,
    val sentenceEndShare: Float = 0.5f,
    val maxDedicationChars: Int = 40,
    val keptCards: Int = 5,
    val snippetMaxShare: Float = A_THIRD,
    val snippetMinSide: Float = 0.08f,
    val snippetPad: Float = 0.015f,
    val snippetNudgeStep: Float = 0.05f,
    val maxTextWords: Int = 150,
    val maxPageWords: Int = 400,
) {
    init {
        require(maxTextWords >= maxWords) { "maxTextWords ($maxTextWords) must be >= maxWords ($maxWords)" }
        require(maxPageWords >= maxWords) { "maxPageWords ($maxPageWords) must be >= maxWords ($maxWords)" }
        require(maxWords >= 1) { "maxWords must be >= 1, was $maxWords" }
        require(sentenceEndShare in 0f..1f) { "sentenceEndShare must be in 0..1, was $sentenceEndShare" }
        require(maxDedicationChars >= 1) { "maxDedicationChars must be >= 1, was $maxDedicationChars" }
        require(keptCards >= 1) { "keptCards must be >= 1, was $keptCards" }
        require(snippetMaxShare > 0f && snippetMaxShare <= 1f) {
            "snippetMaxShare must be in (0, 1], was $snippetMaxShare"
        }
        require(snippetMinSide > 0f && snippetMinSide * snippetMinSide <= snippetMaxShare) {
            "snippetMinSide ($snippetMinSide) must be > 0 and its square within snippetMaxShare ($snippetMaxShare)"
        }
        require(snippetPad >= 0f) { "snippetPad must be >= 0, was $snippetPad" }
        require(snippetNudgeStep > 0f && snippetNudgeStep < 1f) {
            "snippetNudgeStep must be in (0, 1), was $snippetNudgeStep"
        }
    }

    private companion object {
        const val A_THIRD = 1f / 3
    }
}
