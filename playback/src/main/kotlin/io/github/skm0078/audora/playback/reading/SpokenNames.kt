// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/SpokenNames.kt
// Purpose: Names as speech and narration print them: who a quote calls, and which printed words are characters.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.route.NameGenders
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** Pure name checks for [SpeakerTurns]; English only where capitals carry the meaning. */
internal object SpokenNames {
    /** A name [speech] calls at its start or end, printed as one ("Mrs Patel, …", "…, Nora."), not [speaker]. */
    fun printed(speech: String, speaker: Speaker): Speaker? = VOCATIVE.findAll(speech)
        .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .firstOrNull { it.isNotEmpty() && it.lowercase() != speaker.key && properName(it) }
        ?.let { Speaker(it, NameGenders.of(it, Lang.EN)) }
        ?.takeIf { it.gender != SpeakerGender.UNKNOWN || TITLED.containsMatchIn(it.name.orEmpty()) }

    /** A name that is a character on its own: its gender is known, or it is printed as an English name. */
    fun trusted(name: String, lang: Lang): Boolean =
        NameGenders.of(name, lang) != SpeakerGender.UNKNOWN || (lang == Lang.EN && properName(name))

    /** Whether [speech] calls [speaker] by name, as one calls a listener: "Tom," at its start or ", Tom." inside. */
    fun addressed(speaker: Speaker, speech: String): Boolean {
        val name = speaker.name?.substringBefore(' ')?.takeIf { it.length > 1 } ?: return false
        val quoted = Regex.escape(name)
        return Regex("(?:^|[“‘\"\\s])$quoted\\s*[,!?।]|,\\s*$quoted(?![\\p{L}\\p{M}])").containsMatchIn(speech)
    }

    /**
     * Whether [name] is printed as an English name: every word capitalised, and not a word that often opens a
     * sentence of narration ("Rain", "Nothing", "Somewhere").
     */
    fun properName(name: String): Boolean {
        val words = name.trim().split(' ').filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { it.first().isUpperCase() } &&
            words.first().lowercase() !in NOT_NAMES && words.last().lowercase() !in NOT_NAMES
    }

    /** How many words [text] holds. */
    fun words(text: String): Int = WORD.findAll(text).count()

    /** A name called at a speech's start ("Mrs Patel, …") or end ("…, Nora."): a title and up to two words. */
    private const val TITLE = "(?:Mr|Mrs|Ms|Miss|Dr|Inspector|Captain|Aunt|Uncle)"
    private val VOCATIVE = Regex(
        "^[“‘\"]?((?:$TITLE\\.?\\s+)?[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?),\\s+\\p{L}" +
            "|,\\s*((?:$TITLE\\.?\\s+)?[A-Z][a-z]+)[.!?]",
    )

    private val WORD = Regex("[\\p{L}\\p{M}]+")

    private val TITLED = Regex("^$TITLE\\b")

    /** Capitalised words that open narration without being anyone ("Rain fell.", "Nothing moved."). */
    private val NOT_NAMES = setOf(
        "i", "it", "this", "that", "these", "those", "there", "here", "then", "now", "nothing", "something",
        "someone", "somebody", "everyone", "everybody", "everything", "nobody", "no", "none", "all", "both", "each",
        "rain", "snow", "wind", "night", "morning", "evening", "day", "time", "silence", "darkness", "light",
        "fire", "smoke", "water", "outside", "inside", "somewhere", "suddenly", "later", "soon", "once", "after",
        "before", "when", "while", "yes", "oh", "god", "lord", "heaven", "o", "ah", "well", "still", "again",
        "look", "listen", "please", "sorry", "come", "go", "wait", "stop", "hey", "hello", "hi", "thanks", "okay",
        "ok", "right", "good", "fine", "sure", "maybe", "perhaps", "really", "see", "quick", "quickly", "careful",
        "but", "and", "so", "if", "because", "why", "what", "who", "how", "where", "the", "a", "an", "my", "your",
        "our",
    )
}
