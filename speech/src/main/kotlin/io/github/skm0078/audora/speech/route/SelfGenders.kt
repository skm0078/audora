// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/SelfGenders.kt
// Purpose: The gender a speaker tells of themself in Hindi speech ("मैं आऊँगी", "जा रहा हूँ"), for their voice.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/**
 * A Hindi verb agrees with its subject, so a speaker saying "I" tells their gender: the first person future
 * ("आऊँगी", "करूँगा"), a participle before "हूँ" ("जा रही हूँ", "थक गया हूँ"), and "मैं … था/थी". Only these
 * forms, which belong to "I" alone, count; "रहा था" alone could be anyone's, and "मैंने …" agrees with the object.
 * Unknown when the speech tells both genders (a quoted speech within a speech) or neither. English and Bengali verbs
 * carry no gender.
 */
object SelfGenders {
    fun of(speech: String, lang: Lang): SpeakerGender {
        if (lang != Lang.HI) return SpeakerGender.UNKNOWN
        val words = WORDS.findAll(speech).map { it.value }.toList()
        val told = words.indices.mapNotNull { i -> told(words, i) }.toSet()
        return told.singleOrNull() ?: SpeakerGender.UNKNOWN
    }

    private fun told(words: List<String>, i: Int): SpeakerGender? {
        val word = words[i]
        val next = words.getOrNull(i + 1)
        return when {
            FUTURE_MALE.any { word.endsWith(it) } -> SpeakerGender.MALE
            FUTURE_FEMALE.any { word.endsWith(it) } -> SpeakerGender.FEMALE
            next in AM -> participle(word)
            word in WAS && saidByI(words, i) -> WAS.getValue(word)
            else -> null
        }
    }

    /** "रहा", "गई", "जाता" before "हूँ"; a name before it ("मैं सीता हूँ") tells nothing by its ending. */
    private fun participle(word: String): SpeakerGender? = when {
        word in PARTICIPLES -> PARTICIPLES.getValue(word)
        NameGenders.of(word, Lang.HI) != SpeakerGender.UNKNOWN -> null
        word.endsWith(HABITUAL_MALE) -> SpeakerGender.MALE
        word.endsWith(HABITUAL_FEMALE) -> SpeakerGender.FEMALE
        else -> null
    }

    /** "मैं … था" in one clause: "मैं पहुँचा तो दरवाज़ा खुला था" is the door's. */
    private fun saidByI(words: List<String>, at: Int): Boolean {
        val i = words.subList(0, at).lastIndexOf(I)
        return i >= 0 && words.subList(i + 1, at).none { it in CLAUSE_WORDS } && NE !in words
    }

    private val WORDS = Regex("[\\p{L}\\p{M}]+")
    private val FUTURE_MALE = listOf("ूँगा", "ूंगा", "ऊँगा", "ऊंगा")
    private val FUTURE_FEMALE = listOf("ूँगी", "ूंगी", "ऊँगी", "ऊंगी")
    private val AM = setOf("हूँ", "हूं")
    private val PARTICIPLES =
        listOf("रहा", "गया", "चुका", "आया", "हुआ", "बैठा", "लगा").associateWith { SpeakerGender.MALE } +
            listOf("रही", "गई", "गयी", "चुकी", "आई", "आयी", "हुई", "बैठी", "लगी").associateWith { SpeakerGender.FEMALE }
    private const val HABITUAL_MALE = "ता"
    private const val HABITUAL_FEMALE = "ती"
    private val CLAUSE_WORDS = setOf("कि", "तो", "जब", "और", "पर", "लेकिन")
    private val WAS = mapOf("था" to SpeakerGender.MALE, "थी" to SpeakerGender.FEMALE)
    private const val I = "मैं"
    private const val NE = "मैंने"
}
