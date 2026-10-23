// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/Feeling.kt
// Purpose: How one sentence asks to be read, from the cues printed in it (shouted, whispered, wept, "!", "…"; ADR-056).

package io.github.skm0078.audora.core.model

/**
 * The feeling a sentence's own words call for, found by rules (`SentenceFeelings`), first match wins in this order.
 * [NEUTRAL] changes nothing. Only an intent with [ContentIntent.feelings] lets a feeling through.
 */
enum class Feeling {
    /** "shouted", "चिल्लाया", "চেঁচিয়ে": louder and quicker. */
    LOUD,

    /** "whispered", "फुसफुसाया", "ফিসফিস করে": much softer and slower. */
    SOFT,

    /** "sobbed", "रो पड़ी", "কেঁদে": slower, a little softer, a longer pause after. */
    SAD,

    /** An exclamation, "laughed", "हँसकर", "হেসে": a touch quicker and brighter. */
    LIVELY,

    /** A sentence that trails off ("…", "—"): slower, and the pause after is held. */
    TRAILING,

    /** A question: the pause after is a little longer, as a reader waits for an answer. */
    ASKING,

    NEUTRAL,
}
