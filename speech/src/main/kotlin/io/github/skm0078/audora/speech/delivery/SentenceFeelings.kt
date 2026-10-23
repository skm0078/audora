// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/delivery/SentenceFeelings.kt
// Purpose: The feeling a sentence's own words ask for (shouted, whispered, wept, "!", "…"), in en, hi and bn (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.model.Feeling

/**
 * Rules only (stage 1 of the expressive plan): the speech verb or adverb that tells how a line is said, then the
 * sentence's punctuation. A verb beats punctuation ("'Run!' she whispered" is soft, not lively). Cue words are those
 * a printed book uses to tell the reader how a line sounds; mood without a cue word ("The house was empty now.") is
 * left neutral, since a guess at it would often be wrong.
 */
object SentenceFeelings {
    fun of(text: String): Feeling {
        val end = text.trimEnd { it.isWhitespace() || it in CLOSERS }
        return when {
            LOUD.containsMatchIn(text) -> Feeling.LOUD
            SOFT.containsMatchIn(text) -> Feeling.SOFT
            SAD.containsMatchIn(text) -> Feeling.SAD
            LIVELY.containsMatchIn(text) || '!' in text -> Feeling.LIVELY
            end.endsWith("…") || end.endsWith("...") || end.endsWith("—") || end.endsWith("--") -> Feeling.TRAILING
            end.endsWith("?") -> Feeling.ASKING
            else -> Feeling.NEUTRAL
        }
    }

    /** Quote marks and brackets a sentence may end with after its own punctuation. */
    private const val CLOSERS = "\"”’'»)]"

    private val LOUD = cues(
        "shouted|shouting|yelled|yelling|screamed|screaming|roared|bellowed|thundered|shrieked|cried out|called out",
        "चिल्लाया|चिल्लाई|चिल्लाए|चिल्लाकर|चीखा|चीखी|चीखकर|चीख पड़|दहाड़|गरजा|गरजी|ज़ोर से|जोर से|" +
            "চেঁচিয়ে|চেঁচাল|চেঁচালেন|চিৎকার|গর্জে|গর্জন|হুংকার|জোরে",
    )

    private val SOFT = cues(
        "whispered|whispering|murmured|muttered|breathed|softly|quietly|under (?:his|her|their|my) breath",
        "फुसफुसा|धीरे से|धीमे से|धीमी आवाज़|धीमी आवाज|बुदबुदा|" +
            "ফিসফিস|আস্তে আস্তে|আস্তে করে|চুপিচুপি|মৃদু স্বরে|বিড়বিড়",
    )

    private val SAD = cues(
        "sobbed|sobbing|wept|weeping|tearfully|sighed|mourned|grieved|sorrowfully|sadly|in tears",
        "रो पड़|रोने लग|रोते हुए|रोकर|सिसक|आँसू|आंसू|उदास|दुखी होकर|आह भर|" +
            "কেঁদে|কাঁদতে|কান্না|চোখের জল|দীর্ঘশ্বাস|দুঃখে|বিষণ্ণ",
    )

    private val LIVELY = cues(
        "laughed|laughing|exclaimed|cheered|giggled|grinned",
        "हँसकर|हंसकर|हँसते हुए|हंसते हुए|हँस पड़|खुशी से|ख़ुशी से|" +
            "হেসে|হাসতে হাসতে|আনন্দে|উল্লাসে",
    )

    /** English cue words between word boundaries; Hindi and Bengali ones from a word's start (`\\b` knows no Indic). */
    private fun cues(english: String, indic: String): Regex =
        Regex("\\b(?:$english)\\b|(?<![\\p{L}\\p{M}])(?:$indic)", RegexOption.IGNORE_CASE)
}
