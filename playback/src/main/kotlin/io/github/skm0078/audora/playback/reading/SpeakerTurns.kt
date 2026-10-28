// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/SpeakerTurns.kt
// Purpose: Who speaks each quoted sentence of a page, from tags, the paragraph and turn-taking (plan item 63).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.speech.route.NameGenders
import io.github.skm0078.audora.speech.route.QuoteSplitter
import io.github.skm0078.audora.speech.route.SelfGenders
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.route.SpeakerTags
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** One sentence of a page and whether it starts inside a quote an earlier sentence opened ([QuoteCarry]). */
class Sentence(val unit: ReadingUnit, val carried: Boolean)

/**
 * A paragraph's sentences; [continuesSpeech] when the paragraph before ended inside a quote it never closed, the way
 * a speech over several paragraphs is printed (each one reopens the quote), so this one is the same speaker's.
 */
class Paragraph(val sentences: List<Sentence>, val continuesSpeech: Boolean = false)

/**
 * The conversation so far: [known] named speakers, most recent first, for a pronoun to point back to; [last] and
 * [previous] the two who spoke the latest quoted paragraphs, for an untagged line to alternate between; [crowded] once
 * a third speaker joined the run, when turns no longer simply alternate; [called] the one the latest quote called by
 * name ("Mrs Patel, where were you?"), most likely the one who answers. A paragraph with no quote, or a heading, ends
 * the run ([last], [previous], [crowded] and [called] cleared) but keeps the names. [cast]: Hindi and Bengali names
 * that open sentences of narration again and again, so they are the story's people ([recurring]); a
 * heading keeps them.
 */
data class Turns(
    val known: List<Speaker> = emptyList(),
    val last: Speaker? = null,
    val previous: Speaker? = null,
    val crowded: Boolean = false,
    val called: Speaker? = null,
    val cast: Set<String> = emptySet(),
) {
    /**
     * The speaker an untagged line most likely is in a two-way conversation: the one who did not speak last. None
     * once a third speaker joined: with three, the next line could be either of the other two.
     */
    val nextTurn: Speaker? get() = previous.takeIf { last != null && !crowded }

    fun genderOf(key: String): SpeakerGender = known.firstOrNull { it.key == key }?.gender ?: SpeakerGender.UNKNOWN

    /** Whether one of the run's two latest speakers is of [gender], named or not. */
    fun runHas(gender: SpeakerGender): Boolean = listOfNotNull(last, previous).any { it.gender == gender }

    fun brokenRun(): Turns = copy(last = null, previous = null, crowded = false, called = null)

    /**
     * Narration named [speaker] as a sentence's subject ("Mira came home late"): remembered for a later pronoun, and
     * a gender it tells is learnt; the conversation's turns are unchanged.
     */
    fun mentioned(speaker: Speaker, remembered: Int): Turns {
        val key = speaker.key ?: return this
        val gender = speaker.gender.takeUnless { it == SpeakerGender.UNKNOWN } ?: genderOf(key)
        val known = (listOf(speaker.copy(gender = gender)) + known.filter { it.key != key }).take(remembered)
        fun learnt(who: Speaker?) = who?.takeIf { it.key == key }?.copy(gender = gender) ?: who
        return copy(known = known, last = learnt(last), previous = learnt(previous))
    }

    fun spoke(speaker: Speaker, remembered: Int): Turns {
        val known = if (speaker.key == null) known else (listOf(speaker) + known.filter { it.key != speaker.key })
        val next = copy(known = known.take(remembered))
        val who = identity(speaker)
        if (who == last?.let(::identity)) return next
        val third = previous != null && who != identity(previous)
        return next.copy(last = speaker, previous = last, crowded = crowded || third)
    }

    private fun identity(speaker: Speaker): String = speaker.key ?: "${GENERIC}${speaker.gender}"

    private companion object {
        const val GENERIC = "#"
    }
}

/**
 * A quoted sentence is spoken by, in order: its own tag ("said Tom"); the speaker its paragraph's tag names (a speech
 * that runs on over several sentences); the speaker of the paragraph before when this one goes on with that speech;
 * else the other one of the last two speakers (lines of a conversation alternate). A pronoun tag ("she said") points to
 * a speaker of that gender: one tagged earlier in the same paragraph first, then the alternation's next speaker, the
 * last one and the names remembered. A name whose gender is not known yet is taken only from the same paragraph or as
 * the alternation's next speaker, and is taught the gender (the next speaker only when neither of the run's two latest
 * speakers already is of that gender: then "she" is most likely her again); otherwise the line stays a generic female
 * or male speaker. Once a third speaker joins a run, untagged lines no longer alternate. A heading (a new chapter or
 * scene) forgets the conversation. Every guess falls back to null, which keeps the dialogue voice: a wrong character's
 * voice is worse than none.
 */
class SpeakerTurns(
    private val tags: SpeakerTags = SpeakerTags(),
    private val splitter: QuoteSplitter = QuoteSplitter(),
    private val remembered: Int,
) {
    /** [guessed]: the sentences whose speaker's gender is only their name's likely one ([NameGenders.likely]). */
    class Page(val speakers: Map<Int, Speaker>, val end: Turns, val guessed: Set<Int> = emptySet())

    /** [paragraphs]: one page's paragraphs in reading order. */
    fun page(paragraphs: List<Paragraph>, start: Turns): Page {
        val speakers = HashMap<Int, Speaker>()
        var turns = start.copy(cast = start.cast + recurring(paragraphs, tags, ::isQuoted))
        var unplaced = emptyList<Sentence>()
        paragraphs.forEach { paragraph ->
            if (paragraph.sentences.all { it.unit.kind == UnitKind.HEADING }) {
                turns = Turns(cast = turns.cast)
                unplaced = emptyList()
                return@forEach
            }
            turns = paragraph(paragraph, turns, speakers)
            val quoted = paragraph.sentences.filter(::isQuoted)
            if (quoted.isEmpty()) return@forEach
            // An answer calling a name ("“It's me, Dad.”" … "“Lucy!”") tells who said the unplaced line before it.
            turns.called?.takeIf { it.gender != SpeakerGender.UNKNOWN }?.let { asker ->
                unplaced.forEach { speakers[it.unit.index] = asker }
            }
            unplaced = quoted.filter { it.unit.index !in speakers }
        }
        val langs = paragraphs.flatMap { it.sentences }.associate { it.unit.index to it.unit.lang }
        val guessed = HashSet<Int>()
        val settled = speakers.mapValues { (index, who) ->
            settled(who, turns, langs.getValue(index)) { guessed.add(index) }
        }
        return Page(settled, turns, guessed)
    }

    private fun paragraph(paragraph: Paragraph, start: Turns, into: MutableMap<Int, Speaker>): Turns {
        val quoted = paragraph.sentences.filter(::isQuoted)
        // Narration teaches names and genders first ("Mira came home late." then "she said").
        val subjects = ArrayList<Speaker>()
        val turns = paragraph.sentences.filterNot(::isQuoted).fold(start) { acc, sentence ->
            learn(sentence, acc, subjects)
        }
        val speaker = quoted.takeIf { it.isNotEmpty() }?.let { speakers(paragraph, it, turns, subjects, into) }
        return speaker?.let { said ->
            val after = turns.spoke(said, remembered)
            after.copy(called = called(quoted, said, after))
        } ?: turns.brokenRun()
    }

    private fun isQuoted(sentence: Sentence): Boolean = sentence.unit.kind in PROSE &&
        (sentence.carried || splitter.split(sentence.unit.text).any { it.quoted })

    /** [turns] after a sentence of narration; a subject whose gender is known is also put first in [subjects]. */
    private fun learn(sentence: Sentence, turns: Turns, subjects: MutableList<Speaker>): Turns {
        val subject = narrated(sentence, turns) ?: return turns
        if (subject.key != null && subject.gender != SpeakerGender.UNKNOWN) subjects.add(0, subject)
        return turns.mentioned(subject, remembered)
    }

    /** Puts each of [quoted]'s speakers [into] the page's map; the paragraph's speaker, null when none is known. */
    private fun speakers(
        paragraph: Paragraph,
        quoted: List<Sentence>,
        turns: Turns,
        subjects: List<Speaker>,
        into: MutableMap<Int, Speaker>,
    ): Speaker? {
        // A pronoun tag points first to whom this paragraph's narration just named ("Jonah clung on. “No,” he said."),
        // when that name's gender is known: one that is not would be taught a gender on a guess.
        val tagged = ArrayList<Speaker>(subjects)
        val own = quoted.map { sentence ->
            val tag = tags.find(sentence.unit.text, sentence.unit.lang, sentence.carried)
            sentence to tag?.let { resolve(it, turns, tagged) }?.also { if (it.key != null) tagged.add(0, it) }
        }
        val found = own.firstNotNullOfOrNull { it.second } ?: untagged(paragraph, quoted, turns)
        // A speaker saying "I" may tell their gender in Hindi ("मैं आऊँगी"): theirs for this paragraph on.
        val told = quoted.map { SelfGenders.of(splitter.speech(it), it.unit.lang) }
        val speaker = found?.let { toldOwnGender(it, told, own.map { (_, said) -> said }) }
        own.forEachIndexed { i, (sentence, said) ->
            // The narration just before an untagged line may name its speaker: "হরিপদ … “এত দাম!” বাড়ি ফিরতেই মালতী
            // থলেটা দেখল। “মাছ আনোনি?”"
            val beat = if (i > 0 && said == null) beatBefore(paragraph, sentence, turns) else null
            lineSpeaker(beat, said, speaker, told[i])?.let { into[sentence.unit.index] = it }
        }
        return speaker
    }

    /**
     * A named character of known gender the narration right before [sentence] is about, when that narration is not
     * itself quoted. Only a known gender: "Back home, …" opens with a capital too.
     */
    private fun beatBefore(paragraph: Paragraph, sentence: Sentence, turns: Turns): Speaker? {
        val before = paragraph.sentences.getOrNull(paragraph.sentences.indexOf(sentence) - 1) ?: return null
        return before.takeUnless(::isQuoted)?.let { narrated(it, turns) }
            ?.takeIf { it.key != null && it.gender != SpeakerGender.UNKNOWN }
    }

    /**
     * The one [quoted] calls by name, if not [speaker] themselves: a name the conversation knows, or in English a
     * name at the speech's start or end ("Mrs Patel, where…", "…breathe, Nora.") with a title or a gender of its
     * own: "Out, …" or "Fine, …" open speeches as often as names do.
     */
    private fun called(quoted: List<Sentence>, speaker: Speaker, turns: Turns): Speaker? {
        val speech = quoted.joinToString(" ") { splitter.speech(it) }
        // A speech that is only a name ("Raj," said Tom) answers a question; it calls no one.
        val known = turns.known.firstOrNull {
            it.key != speaker.key && SpokenNames.addressed(it, speech) &&
                SpokenNames.words(speech) > SpokenNames.words(it.name.orEmpty())
        }
        return known ?: if (quoted.first().unit.lang == Lang.EN) SpokenNames.printed(speech, speaker) else null
    }

    /**
     * A paragraph whose quotes carry no tag: the subject of the narration just before its first quote (an action
     * beat), then the speech it goes on with, then the one the last quote called by name, then the other one of a
     * two-way conversation; never a name the quote itself calls out to ("My phone died, Tom."): that one is listening.
     */
    private fun untagged(paragraph: Paragraph, quoted: List<Sentence>, turns: Turns): Speaker? {
        val first = paragraph.sentences.indexOf(quoted.first())
        val beat = paragraph.sentences.take(first).lastOrNull()?.let { narrated(it, turns) }
            ?.let { subject -> if (subject.key == null) pronoun(subject.gender, turns, emptyList()) else subject }
        val spoken = quoted.joinToString(" ") { it.unit.text }
        // A quote calling the beat's subject by name is said to them, most likely by the other one in the scene.
        val other = beat?.takeIf { SpokenNames.addressed(it, spoken) }?.let { listener ->
            turns.known.firstOrNull { it.key != null && it.key != listener.key }
        }
        val candidates = listOfNotNull(
            beat,
            other,
            turns.last.takeIf { paragraph.continuesSpeech },
            turns.called?.takeIf { it.key != turns.last?.key },
            turns.nextTurn,
            turns.last.takeIf { turns.nextTurn != null },
        )
        return candidates.firstOrNull { !SpokenNames.addressed(it, spoken) }
    }

    /**
     * The subject of a sentence of narration when it can be trusted as a character: a pronoun with a gender, a name
     * the conversation already knows, one whose gender its name or verb tells, or an English name as printed
     * ("Inspector Ward", "Kit"; its gender stays unknown until a tag or pronoun tells it, so it gets no voice yet).
     */
    private fun narrated(sentence: Sentence, turns: Turns): Speaker? {
        val lang = sentence.unit.lang
        val subject = sentence.takeIf { it.unit.kind in PROSE }?.let { tags.subjectOf(it.unit.text, lang) }
        val key = subject?.key
        val known = key?.let { turns.known.firstOrNull { it.key == key } }
        // A verb's ending may teach a known name's gender ("मीना … घुसी"), but never makes a word a character:
        // "कोई जवाब नहीं आया" opens with "no one".
        return when {
            subject == null -> null
            key == null -> subject.takeIf { it.gender != SpeakerGender.UNKNOWN }
            SpokenNames.trusted(subject.name.orEmpty(), lang) || key in turns.cast -> subject
            known != null -> if (subject.gender != SpeakerGender.UNKNOWN) known.copy(gender = subject.gender) else known
            else -> null
        }
    }
}

/**
 * A named speaker whose gender the page told only after their lines ("Dr Okafor smiled. “She's awake.”" … "she
 * said"), or never: what the page's end knows, else what a Hindi or Bengali name's ending most likely tells
 * ([NameGenders.likely], [onGuess] then called). Otherwise the narrator would read them, in a voice that is the wrong
 * gender half the time.
 */
private fun settled(speaker: Speaker, end: Turns, lang: Lang, onGuess: () -> Unit): Speaker {
    val key = speaker.key
    if (key == null || speaker.gender != SpeakerGender.UNKNOWN) return speaker
    val learnt = end.genderOf(key)
    val gender = learnt.takeUnless { it == SpeakerGender.UNKNOWN }
        ?: NameGenders.likely(key, lang).also { if (it != SpeakerGender.UNKNOWN) onGuess() }
    return speaker.copy(gender = gender)
}

/** [who], the paragraph's speaker, with the gender their own words tell ([told]) in a line no one else is [said]. */
private fun toldOwnGender(who: Speaker, told: List<SpeakerGender>, said: List<Speaker?>): Speaker {
    val self = told.filterIndexed { i, _ -> said[i].let { it == null || it.key == who.key } }
        .firstOrNull { it != SpeakerGender.UNKNOWN }
    return if (who.gender == SpeakerGender.UNKNOWN && self != null) who.copy(gender = self) else who
}

/**
 * One line's speaker: the [beat] before it, else its own tag ([said]) when that names someone other than the
 * [paragraph]'s speaker, else the paragraph's speaker; then given the gender the line's own words tell ([self]).
 */
private fun lineSpeaker(beat: Speaker?, said: Speaker?, paragraph: Speaker?, self: SpeakerGender): Speaker? {
    val who = beat ?: said?.takeUnless { it.key != null && it.key == paragraph?.key } ?: paragraph
    return when {
        self == SpeakerGender.UNKNOWN -> who
        who == null -> Speaker(null, self)
        who.gender == SpeakerGender.UNKNOWN -> who.copy(gender = self)
        else -> who
    }
}

/** What [sentence]'s quotes say, without the narration around them. */
private fun QuoteSplitter.speech(sentence: Sentence): String =
    split(sentence.unit.text, sentence.carried).filter { it.quoted }.joinToString(" ") { it.text }

/** [earlier]: the names tagged before in the same paragraph, most recent first. */
private fun resolve(tag: Speaker, turns: Turns, earlier: List<Speaker>): Speaker? {
    val key = tag.key
    return when {
        key != null -> if (tag.gender == SpeakerGender.UNKNOWN) tag.copy(gender = turns.genderOf(key)) else tag
        tag.gender == SpeakerGender.UNKNOWN -> null
        else -> pronoun(tag.gender, turns, earlier)
    }
}

private fun pronoun(gender: SpeakerGender, turns: Turns, earlier: List<Speaker>): Speaker {
    val named = (earlier + listOfNotNull(turns.nextTurn, turns.last) + turns.known).filter { it.key != null }
    // The other speaker learns the gender only if "she" can't simply be the run's own speaker of that gender again.
    val other = turns.nextTurn.takeUnless { turns.runHas(gender) }
    val learner = (earlier + listOfNotNull(other)).filter { it.key != null }
    val match = named.firstOrNull { it.gender == gender }
        ?: learner.firstOrNull { it.gender == SpeakerGender.UNKNOWN }
    return match?.copy(gender = gender) ?: Speaker(null, gender)
}

/** Languages printed without capitals, whose names are known by repetition ([SpeakerTurns]). */
private val CASTLESS = setOf(Lang.HI, Lang.BN)

/** How often a word must open narration on one page to be taken as a person's name. */
private const val CAST_MIN_SUBJECTS = 2

/**
 * Hindi and Bengali print no capitals, so an unknown name ("बिरजू", "পল্টু") cannot be told from a noun by its look.
 * A word that opens [Lang.HI] or [Lang.BN] narration (not [quoted]) at least [CAST_MIN_SUBJECTS] times on the page is
 * taken as one of the story's people (ADR-077): "बिरजू खेत की मेड़ पर बैठा", "बिरजू ने मुँह फेर लिया". A noun opens
 * a sentence once ("बादल घिर आए थे") and stays untrusted.
 */
private fun recurring(paragraphs: List<Paragraph>, tags: SpeakerTags, quoted: (Sentence) -> Boolean): Set<String> =
    paragraphs.asSequence()
        .flatMap { it.sentences }
        .filter { it.unit.lang in CASTLESS && it.unit.kind in PROSE && !quoted(it) }
        .mapNotNull { tags.subjectOf(it.unit.text, it.unit.lang)?.key }
        .groupingBy { it }.eachCount()
        .filterValues { it >= CAST_MIN_SUBJECTS }.keys
