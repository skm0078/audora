// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/SpeakerTurns.kt
// Purpose: Who speaks each quoted sentence of a page, from tags, the paragraph and turn-taking (plan item 63).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.route.QuoteSplitter
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.route.SpeakerTags
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** One sentence of a page and whether it starts inside a quote an earlier sentence opened ([QuoteCarry]). */
class Sentence(val unit: ReadingUnit, val carried: Boolean)

/**
 * The conversation so far: [known] named speakers, most recent first, for a pronoun to point back to; [last] and
 * [previous] the two who spoke the latest quoted paragraphs, for an untagged line to alternate between. A paragraph
 * with no quote, or a heading, ends the run ([last] and [previous] cleared) but keeps the names.
 */
data class Turns(val known: List<Speaker> = emptyList(), val last: Speaker? = null, val previous: Speaker? = null) {
    /** The speaker an untagged line most likely is in a two-way conversation: the one who did not speak last. */
    val nextTurn: Speaker? get() = previous.takeIf { last != null }

    fun genderOf(key: String): SpeakerGender = known.firstOrNull { it.key == key }?.gender ?: SpeakerGender.UNKNOWN

    fun brokenRun(): Turns = copy(last = null, previous = null)

    fun spoke(speaker: Speaker, remembered: Int): Turns {
        val known = if (speaker.key == null) known else (listOf(speaker) + known.filter { it.key != speaker.key })
        val next = copy(known = known.take(remembered))
        return if (identity(speaker) == last?.let(::identity)) next else next.copy(last = speaker, previous = last)
    }

    private fun identity(speaker: Speaker): String = speaker.key ?: "${GENERIC}${speaker.gender}"

    private companion object {
        const val GENERIC = "#"
    }
}

/**
 * A quoted sentence is spoken by, in order: its own tag ("said Tom"); the speaker its paragraph's tag names (a speech
 * that runs on over several sentences); else the other one of the last two speakers (lines of a conversation
 * alternate). A pronoun tag ("she said") points to the most recent speaker of that gender, the alternation's next
 * speaker first, and teaches that speaker the gender; with no match it stays a generic female or male speaker. Every
 * guess falls back to null, which keeps the dialogue voice: a wrong character's voice is worse than none.
 */
class SpeakerTurns(
    private val tags: SpeakerTags = SpeakerTags(),
    private val splitter: QuoteSplitter = QuoteSplitter(),
    private val remembered: Int,
) {
    class Page(val speakers: Map<Int, Speaker>, val end: Turns)

    /** [paragraphs]: one page's paragraphs in reading order, each a run of its sentences. */
    fun page(paragraphs: List<List<Sentence>>, start: Turns): Page {
        val speakers = HashMap<Int, Speaker>()
        var turns = start
        paragraphs.forEach { paragraph ->
            turns = paragraph(paragraph, turns, speakers)
        }
        return Page(speakers, turns)
    }

    private fun paragraph(sentences: List<Sentence>, turns: Turns, into: MutableMap<Int, Speaker>): Turns {
        val quoted = sentences.filter { it.unit.kind in PROSE && (it.carried || hasQuote(it.unit.text)) }
        if (quoted.isEmpty()) return turns.brokenRun()
        val own = quoted.map { sentence ->
            sentence to tags.find(sentence.unit.text, sentence.unit.lang, sentence.carried)?.let { resolve(it, turns) }
        }
        val speaker = own.firstNotNullOfOrNull { it.second } ?: turns.nextTurn
        own.forEach { (sentence, tagged) ->
            (tagged ?: speaker)?.let { into[sentence.unit.index] = it }
        }
        return speaker?.let { turns.spoke(it, remembered) } ?: turns.brokenRun()
    }

    private fun hasQuote(text: String): Boolean = splitter.split(text).any { it.quoted }

    private fun resolve(tag: Speaker, turns: Turns): Speaker? {
        val key = tag.key
        return when {
            key != null -> if (tag.gender == SpeakerGender.UNKNOWN) tag.copy(gender = turns.genderOf(key)) else tag
            tag.gender == SpeakerGender.UNKNOWN -> null
            else -> pronoun(tag.gender, turns)
        }
    }

    private fun pronoun(gender: SpeakerGender, turns: Turns): Speaker {
        val candidates = listOfNotNull(turns.nextTurn, turns.last) + turns.known
        val named = candidates.filter { it.key != null }
        val match =
            named.firstOrNull { it.gender == gender } ?: named.firstOrNull { it.gender == SpeakerGender.UNKNOWN }
        return match?.copy(gender = gender) ?: Speaker(null, gender)
    }
}
