// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/SpeakerTags.kt
// Purpose: Finds who speaks a sentence's quoted speech from its dialogue tag ("said Mira", "राम ने कहा"), plan item 63.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/**
 * Who says a quote: a [name] ("Mira", "the old man"), or only a pronoun ([name] null: "she said"). [gender] comes
 * from the pronoun, a title or a Hindi verb ending; [SpeakerGender.UNKNOWN] otherwise. [key] is the name as a
 * character list keeps it: case and spacing folded, so "Mira" and "MIRA" are one character.
 */
data class Speaker(val name: String?, val gender: SpeakerGender) {
    val key: String? get() = name?.trim()?.replace(SPACES, " ")?.lowercase()

    private companion object {
        val SPACES = Regex("""\s+""")
    }
}

/**
 * Looks only at the narration right next to a quote in the same sentence: after it ("…,” said Tom.) first, then
 * before it (Tom said, “…”). Pure, one sentence at a time; carrying a speaker to the sentences after is the caller's
 * (a speech that runs on, the next line of a conversation). Null when the sentence has no quote or no tag.
 */
class SpeakerTags(private val splitter: QuoteSplitter = QuoteSplitter()) {
    fun find(text: String, lang: Lang, startsInQuote: Boolean = false): Speaker? {
        val parts = splitter.split(text, startsInQuote)
        val firstQuote = parts.indexOfFirst { it.quoted }
        val patterns = PATTERNS[lang]
        if (firstQuote < 0 || patterns == null) return null
        val after = parts.getOrNull(firstQuote + 1)?.takeUnless { it.quoted }?.text
        val before = parts.getOrNull(firstQuote - 1)?.takeUnless { it.quoted }?.text
        return after?.let(patterns::after) ?: before?.let(patterns::before)
    }

    private interface Patterns {
        fun after(narration: String): Speaker?

        fun before(narration: String): Speaker?
    }

    /** "…,” said Tom. / “…,” Tom said. / “…,” said the old man. / Mrs Gupta asked, “…” / She said, “…” */
    private object English : Patterns {
        private const val VERB = "(?:said|says|asked|asks|replied|answered|shouted|cried|whispered|muttered|" +
            "called|added|continued|began|explained|exclaimed|yelled|snapped|murmured|demanded|insisted|laughed|" +
            "sighed|agreed|admitted|declared|interrupted|went on|told \\w+)"
        private const val NAME = "((?:(?:Mr|Mrs|Ms|Miss|Dr|Sir|Lady|Uncle|Aunt)\\.?\\s+)?[A-Z][\\p{L}'’-]+" +
            "(?:\\s+[A-Z][\\p{L}'’-]+){0,2})"
        private const val THE = "((?:the|a|an|his|her|their|my|our)\\s+(?:[a-z]+\\s+){0,2}[a-z]+)"
        private const val PRONOUN = "(he|she|i|I|He|She)"
        private const val ADVERB = "(?:\\w+ly\\s+)?"
        private val verbThenWho = Regex("^\\W*$VERB\\s+(?:$NAME|$THE|$PRONOUN)\\b")
        private val whoThenVerb = Regex("^\\W*(?:$NAME|$PRONOUN)\\s+$ADVERB$VERB\\b")
        private val whoThenVerbAtEnd = Regex("(?:^|[\\s,;])(?:$NAME|$THE|$PRONOUN)\\s+$ADVERB$VERB\\W*$")
        private val NOT_NAMES = setOf(
            "The", "A", "An", "But", "And", "Then", "When", "So", "Yet", "Now", "It", "They", "We", "You", "This",
            "That", "There", "His", "Her", "Their", "My", "Our",
        )
        private val MALE = setOf("man", "boy", "king", "father", "brother", "son", "husband", "uncle", "lord", "sir")
        private val FEMALE = setOf(
            "woman", "girl", "queen", "mother", "sister", "daughter", "wife", "aunt", "lady", "madam",
        )

        override fun after(narration: String): Speaker? = verbThenWho.find(narration)?.let(::speaker)
            ?: whoThenVerb.find(narration)?.let(::speaker)

        override fun before(narration: String): Speaker? = whoThenVerbAtEnd.find(narration)?.let(::speaker)

        private fun speaker(match: MatchResult): Speaker? {
            val groups = match.groupValues.drop(1).filter { it.isNotEmpty() }
            val who = groups.firstOrNull() ?: return null
            return when {
                who.lowercase() in PRONOUNS -> Speaker(null, pronounGender(who.lowercase()))
                who.first().isUpperCase() -> named(who)
                else -> described(who)
            }
        }

        private fun named(who: String): Speaker? {
            if (who.substringBefore(' ') in NOT_NAMES) return null
            val title = who.substringBefore(' ').trimEnd('.')
            val gender = when (title) {
                "Mr", "Sir", "Uncle" -> SpeakerGender.MALE
                "Mrs", "Ms", "Miss", "Lady", "Aunt" -> SpeakerGender.FEMALE
                else -> SpeakerGender.UNKNOWN
            }
            return Speaker(who, gender)
        }

        /** "the old man to his son" is "the old man": the phrase ends at the first word that is not part of it. */
        private fun described(phrase: String): Speaker {
            val words = phrase.split(' ')
            val kept = listOf(words.first()) + words.drop(1).takeWhile { it !in PHRASE_ENDS }
            val who = kept.joinToString(" ")
            return Speaker(who, nounGender(who))
        }

        private fun nounGender(phrase: String): SpeakerGender {
            val noun = phrase.substringAfterLast(' ')
            return when (noun) {
                in MALE -> SpeakerGender.MALE
                in FEMALE -> SpeakerGender.FEMALE
                else -> SpeakerGender.UNKNOWN
            }
        }

        private fun pronounGender(pronoun: String) = when (pronoun) {
            "he" -> SpeakerGender.MALE
            "she" -> SpeakerGender.FEMALE
            else -> SpeakerGender.UNKNOWN
        }

        private val PRONOUNS = setOf("he", "she", "i")
        private val PHRASE_ENDS = setOf(
            "to", "with", "as", "and", "in", "at", "from", "before", "after", "again", "softly", "quietly", "loudly",
            "who", "that", "then", "but",
        )
    }

    /** "राम ने कहा", "सीता बोली", "उसने पूछा": the word before ने or the verb; the verb's ending gives the gender. */
    private object Hindi : Patterns {
        // Devanagari letters and signs, without the dandas (U+0964, U+0965) that end a sentence.
        private const val WORD = "([\\u0900-\\u0963\\u0966-\\u097F]+)"
        private const val VERB = "(कहा|पूछा|बोला|बोली|बोले|चिल्लाया|चिल्लाई|जवाब दिया|उत्तर दिया|कहने लगा|कहने लगी|" +
            "पुकारा|फुसफुसाया|फुसफुसाई)"
        private val tag = Regex("$WORD\\s+(?:ने\\s+)?$VERB")
        private val PRONOUNS = setOf("उसने", "वह", "वो", "मैंने", "मैं", "उन्होंने", "वे")
        private val NOT_NAMES = setOf("तब", "फिर", "और", "तो", "ने", "भी", "यह", "ये", "कुछ", "धीरे", "ज़ोर", "जोर")

        override fun after(narration: String): Speaker? = tag.find(narration)?.let(::speaker)

        override fun before(narration: String): Speaker? = tag.findAll(narration).lastOrNull()?.let(::speaker)

        private fun speaker(match: MatchResult): Speaker? {
            val (word, verb) = match.destructured
            val gender = when {
                verb.endsWith("ा") || verb.endsWith("ाया") -> SpeakerGender.MALE
                verb.endsWith("ी") || verb.endsWith("ाई") -> SpeakerGender.FEMALE
                else -> SpeakerGender.UNKNOWN
            }.let { if (verb in NEUTRAL) SpeakerGender.UNKNOWN else it }
            return when (word) {
                in PRONOUNS -> Speaker(null, gender)
                in NOT_NAMES -> null
                else -> Speaker(word, gender)
            }
        }

        /** Past forms after ने agree with the object, not the speaker. */
        private val NEUTRAL = setOf("कहा", "पूछा", "जवाब दिया", "उत्तर दिया", "पुकारा")
    }

    /** "রহিম বলল", "মা বললেন", "সে জিজ্ঞেস করল": the word before the verb; Bengali verbs carry no gender. */
    private object Bengali : Patterns {
        private const val WORD = "([\\u0980-\\u09FF]+)"
        private const val VERB = "(বলল|বললেন|বলে উঠল|বলেছিল|জিজ্ঞেস করল|জিজ্ঞাসা করল|উত্তর দিল|চেঁচিয়ে উঠল|ডাকল)"
        private val tag = Regex("$WORD\\s+$VERB")
        private val PRONOUNS = setOf("সে", "তিনি", "আমি", "ও", "তারা")
        private val NOT_NAMES = setOf("তখন", "তারপর", "আর", "এবং", "আবার", "আস্তে", "জোরে")

        override fun after(narration: String): Speaker? = tag.find(narration)?.let(::speaker)

        override fun before(narration: String): Speaker? = tag.findAll(narration).lastOrNull()?.let(::speaker)

        private fun speaker(match: MatchResult): Speaker? {
            val word = match.groupValues[1]
            return when (word) {
                in PRONOUNS -> Speaker(null, SpeakerGender.UNKNOWN)
                in NOT_NAMES -> null
                else -> Speaker(word, SpeakerGender.UNKNOWN)
            }
        }
    }

    private companion object {
        val PATTERNS: Map<Lang, Patterns> = mapOf(Lang.EN to English, Lang.HI to Hindi, Lang.BN to Bengali)
    }
}
