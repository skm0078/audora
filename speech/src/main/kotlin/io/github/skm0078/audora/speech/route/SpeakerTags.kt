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
        val found = after?.let(patterns::after) ?: before?.let(patterns::before)
        return found?.let { withNameGender(it, lang) }
    }

    /**
     * Who a sentence of plain narration is about, for the quote that follows it in the same paragraph (an action
     * beat: "Ravi stopped at a stall. “How much?”"): its subject, a name, a role ("their mother") or a pronoun. Only
     * the sentence's start is read; null when it opens with anything else. The caller decides whether to trust a
     * name (one it knows, or one whose gender [NameGenders] knows): "Waves broke over the rail" opens with a noun.
     */
    fun subjectOf(text: String, lang: Lang): Speaker? {
        if (splitter.split(text).any { it.quoted }) return null
        val patterns = PATTERNS[lang] ?: return null
        return patterns.subject(text.trim())?.let { withNameGender(it, lang) }
    }

    private fun withNameGender(speaker: Speaker, lang: Lang): Speaker {
        val name = speaker.name ?: return speaker
        if (speaker.gender != SpeakerGender.UNKNOWN) return speaker
        return speaker.copy(gender = NameGenders.of(name, lang))
    }

    private interface Patterns {
        fun after(narration: String): Speaker?

        fun before(narration: String): Speaker?

        fun subject(sentence: String): Speaker?
    }

    /** "…,” said Tom. / “…,” Tom said. / “…,” said the old man. / Mrs Gupta asked, “…” / She said, “…” */
    private object English : Patterns {
        private const val VERB = "(?:said|says|asked|asks|replied|answered|shouted|cried|whispered|muttered|" +
            "called|added|continued|began|explained|exclaimed|yelled|snapped|murmured|demanded|insisted|laughed|" +
            "sighed|agreed|admitted|declared|interrupted|went on|told \\w+|roared|stammered|stuttered|sobbed|" +
            "gasped|groaned|hissed|growled|pleaded|begged|warned|announced|remarked|suggested|protested|" +
            "repeated|screamed|shouted|bellowed|breathed|whimpered|grumbled|called out|cried out)"
        private const val NAME = "((?:(?:Mr|Mrs|Ms|Miss|Dr|Sir|Lady|Uncle|Aunt)\\.?\\s+)?[A-Z][\\p{L}'’-]+" +
            "(?:\\s+[A-Z][\\p{L}'’-]+){0,2})"
        private const val THE = "((?:the|a|an|his|her|their|my|our)\\s+(?:[a-z]+\\s+){0,2}[a-z]+)"
        private const val PRONOUN = "(he|she|i|I|He|She)"
        private const val ADVERB = "(?:\\w+ly\\s+)?"
        private val verbThenWho = Regex("^\\W*$VERB\\s+(?:$NAME|$THE|$PRONOUN)\\b")
        private val whoThenVerb = Regex("^\\W*(?:$NAME|$PRONOUN)\\s+$ADVERB$VERB\\b")
        private val whoThenVerbAtEnd = Regex("(?:^|[\\s,;])(?:$NAME|$THE|$PRONOUN)\\s+$ADVERB$VERB\\W*$")
        private val subjectThenVerb = Regex("^(?:$NAME|$THE|$PRONOUN)\\s+[a-z]+\\b")
        private val ARTICLE_START = Regex("^(?:The|A|An|His|Her|Their|My|Our)\\b")
        private val NOT_NAMES = setOf(
            "The", "A", "An", "But", "And", "Then", "When", "So", "Yet", "Now", "It", "They", "We", "You", "This",
            "That", "There", "His", "Her", "Their", "My", "Our",
        )
        private val MALE = setOf(
            "man", "boy", "king", "father", "brother", "son", "husband", "uncle", "lord", "sir", "grandfather",
            "grandpa", "dad", "captain", "gentleman", "monk", "priest",
        )
        private val FEMALE = setOf(
            "woman", "girl", "queen", "mother", "sister", "daughter", "wife", "aunt", "lady", "madam", "grandmother",
            "grandma", "mum", "mom", "nurse", "widow",
        )

        override fun after(narration: String): Speaker? = verbThenWho.find(narration)?.let(::speaker)
            ?: whoThenVerb.find(narration)?.let(::speaker)

        override fun before(narration: String): Speaker? = whoThenVerbAtEnd.find(narration)?.let(::speaker)

        /** "Ravi stopped…", "Their mother put down…", "She folded her arms." */
        override fun subject(sentence: String): Speaker? {
            val start = sentence.trimStart('“', '"', '‘', '(', ' ')
            val lowered = ARTICLE_START.replace(start) { it.value.lowercase() }
            return subjectThenVerb.find(lowered)?.let(::speaker)
        }

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

        /**
         * "the old man to his son" is "the old man": the phrase ends at the first word that is not part of it, or
         * at its first word for a man or a woman ("their mother put down…" is "their mother").
         */
        private fun described(phrase: String): Speaker {
            val words = phrase.split(' ')
            val kept = listOf(words.first()) + words.drop(1).takeWhile { it !in PHRASE_ENDS }
            val noun = kept.drop(1).indexOfFirst { it in MALE || it in FEMALE }
            val who = (if (noun >= 0) kept.take(noun + 2) else kept).joinToString(" ")
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
            "who", "that", "then", "but", "behind", "beside", "near", "by", "of", "on", "at", "into", "across",
        )
    }

    /**
     * Hindi and Bengali put the subject first and the verb last: in a tag ("पिताजी ने चश्मा उतारते हुए पूछा", "দাদা
     * হেসে বলল") the speaker is the clause's first word, or the word before ने, never simply the word before the verb
     * (an adverb or participle: "धीरे से", "হেসে"). [verbs]: speech verbs, longest first. [skip]: words a clause may
     * open with that are not its subject. [objectMarkers]: participles whose noun before them is their object, not the
     * subject ("দীর্ঘশ্বাস ফেলে"). [pronouns]: subject pronouns; Hindi's own ने forms (उसने) are pronouns too.
     */
    private abstract class SubjectFirst(
        verbs: List<String>,
        private val skip: Set<String>,
        private val pronouns: Set<String>,
        private val objectMarkers: Set<String> = emptySet(),
    ) : Patterns {
        private val verb = Regex(
            "(?<![\\p{L}\\p{M}])(?:${verbs.sortedByDescending { it.length }.joinToString("|")})(?![\\p{L}\\p{M}])",
        )

        /** The tag after a quote: its first clause, up to the sentence's end. */
        override fun after(narration: String): Speaker? {
            val clause = narration.substringBefore('।').substringBefore('.')
            val said = verb.find(clause) ?: return null
            return speakerOf(clause.substring(0, said.range.first), said.value)
        }

        /** The tag before a quote: the narration's last sentence, which ends on the verb ("मंत्री … कहिलेন,"). */
        override fun before(narration: String): Speaker? {
            val clause = narration.substringAfterLast('।').trim()
            val said = verb.findAll(clause).lastOrNull() ?: return null
            return speakerOf(clause.substring(0, said.range.first), said.value)
        }

        override fun subject(sentence: String): Speaker? {
            val words = words(sentence)
            if (words.size < 2) return null
            return pick(words, sentence.trimEnd().trimEnd('।', '.', '!', '?').substringAfterLast(' '))
        }

        private fun speakerOf(lead: String, verb: String): Speaker? = pick(words(lead), verb)

        /** The subject among a clause's [words]; [verb] may tell its gender. */
        private fun pick(words: List<String>, verb: String): Speaker? {
            agent(words)?.let { return it }
            val gender = genderOf(verb)
            val at = words.indexOfFirst { it !in skip }
            if (at < 0) return null
            val word = words[at]
            if (words.getOrNull(at + 1) in objectMarkers) return null
            if (word in pronouns) return Speaker(null, gender)
            return Speaker(withTitle(words, at), gender)
        }

        /** Hindi's "X ने": the doer, whatever comes first. */
        open fun agent(words: List<String>): Speaker? = null

        /** What a verb's ending tells of its subject's gender; Bengali verbs tell none. */
        open fun genderOf(verb: String): SpeakerGender = SpeakerGender.UNKNOWN

        /** "सेठ जी", "মাস্টার মশাই": an honorific after a name belongs to it. */
        protected fun withTitle(words: List<String>, at: Int): String {
            val next = words.getOrNull(at + 1)
            return if (next != null && next in HONORIFICS) "${words[at]} $next" else words[at]
        }

        protected fun words(text: String): List<String> = WORDS.findAll(text).map { it.value }.toList()

        private companion object {
            val WORDS = Regex("[\\p{L}\\p{M}]+")
            val HONORIFICS = setOf("जी", "साहब", "বাবু", "মশাই")
        }
    }

    /** "राम ने कहा", "सीता बोली", "उसने धीरे से कहा", "वह आँसू पोंछते हुए बोली". */
    private object Hindi : SubjectFirst(
        verbs = listOf(
            "कहा", "पूछा", "बोला", "बोली", "बोले", "बोलीं", "चिल्लाया", "चिल्लाई", "चिल्लाए", "चीखा", "चीखी", "चीखे",
            "जवाब दिया", "उत्तर दिया", "कहने लगा", "कहने लगी", "कहने लगे", "पुकारा", "फुसफुसाया", "फुसफुसाई",
            "मुस्कुराया", "मुस्कुराई", "मुस्कराया", "मुस्कराई", "बुदबुदाया", "बुदबुदाई", "गरजा", "गरजी", "डाँटा",
            "डांटा", "समझाया", "बताया", "पूछने लगा", "पूछने लगी",
        ),
        skip = setOf("तब", "फिर", "और", "तो", "भी", "यह", "ये", "कुछ", "अब", "आख़िर", "आखिर", "अचानक"),
        pronouns = setOf("वह", "वो", "वे", "मैं", "हम", "तुम", "आप"),
    ) {
        private val doer = Regex("([\\p{L}\\p{M}]+)(?:\\s+(जी))?\\s+ने(?![\\p{L}\\p{M}])")
        private val ownNe = setOf("उसने", "उन्होंने", "मैंने", "हमने", "तुमने", "आपने", "इसने", "इन्होंने")

        override fun agent(words: List<String>): Speaker? {
            words.firstOrNull { it in ownNe }?.let { return Speaker(null, SpeakerGender.UNKNOWN) }
            val match = doer.find(words.joinToString(" ")) ?: return null
            val name = match.groupValues[1]
            if (name in setOf("तब", "फिर", "और")) return null
            val title = match.groupValues[2]
            return Speaker(if (title.isEmpty()) name else "$name $title", SpeakerGender.UNKNOWN)
        }

        /** An intransitive verb agrees with its subject: बोली, चीखी, गई are hers; बोला, आया, गया his. */
        override fun genderOf(verb: String): SpeakerGender {
            val last = verb.substringAfterLast(' ')
            return when {
                last in NEUTRAL -> SpeakerGender.UNKNOWN
                last.endsWith("ी") || last.endsWith("ई") || last.endsWith("ीं") -> SpeakerGender.FEMALE
                last.endsWith("ा") || last.endsWith("े") || last.endsWith("ए") -> SpeakerGender.MALE
                else -> SpeakerGender.UNKNOWN
            }
        }

        /** Past forms after ने agree with the object, not the speaker; "नहीं" and "है" are not verb endings. */
        private val NEUTRAL = setOf(
            "कहा", "पूछा", "दिया", "पुकारा", "डाँटा", "डांटा", "समझाया", "बताया", "नहीं", "है", "हैं", "देखा",
        )
    }

    /** "রহিম বলল", "মা বললেন", "বৃদ্ধ হাসিয়া কহিল" (sadhu), "সে জিজ্ঞেস করল". Bengali verbs carry no gender. */
    private object Bengali : SubjectFirst(
        verbs = listOf(
            "বলল", "বললো", "বললেন", "বলে উঠল", "বলে উঠলেন", "বলেছিল", "বলিল", "বলিলেন", "কহিল", "কহিলেন",
            "জিজ্ঞেস করল", "জিজ্ঞেস করলেন", "জিজ্ঞাসা করল", "জিজ্ঞাসা করলেন", "জিজ্ঞাসা করিল", "জিজ্ঞাসা করিলেন",
            "প্রশ্ন করল", "উত্তর দিল", "উত্তর দিলেন", "উত্তর দিলো", "চেঁচিয়ে উঠল", "চেঁচিয়ে উঠলেন", "চেঁচাল",
            "গর্জে উঠল", "গর্জে উঠলেন", "গর্জিয়া উঠিল", "গর্জিয়া উঠিলেন", "ডাকল", "ডাকিল", "নিবেদন করিলেন",
            "নিবেদন করল", "হাসল", "হাসিল", "ফিসফিস করে বলল",
        ),
        skip = setOf("তখন", "তারপর", "আর", "এবং", "আবার", "হঠাৎ", "শেষে", "পরে", "কিছুক্ষণ", "এবার"),
        pronouns = setOf("সে", "তিনি", "আমি", "ও", "তারা", "উনি", "আমরা", "তুমি"),
        objectMarkers = setOf(
            "ফেলে", "করে", "নিয়ে", "দিয়ে", "ছেড়ে", "তুলে", "নামিয়ে", "নামাইয়া", "মুছে", "মুছিয়া", "ফেলিয়া",
            "করিয়া",
        ),
    )

    private companion object {
        val PATTERNS: Map<Lang, Patterns> = mapOf(Lang.EN to English, Lang.HI to Hindi, Lang.BN to Bengali)
    }
}
