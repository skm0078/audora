// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DeliveryConfig.kt
// Purpose: Tunables for reading a book to suit it (ADR-056): telling its intent, and each intent's and feeling's sound.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling

/**
 * How one [ContentIntent] is read, on top of the listener's own speed and pace. Multipliers: 1 changes nothing.
 *
 * @property rate the voice's speed for narration.
 * @property dialogueRate the speed for a sentence with quoted speech, relative to normal (not to [rate]).
 * @property pauseScale the silence after each sentence (paragraph and heading pauses included).
 * @property headingPauseScale extra on top of [pauseScale] after a heading, so a new section has room.
 * @property clauseScale the pauses at dashes, colons and the like inside a sentence (RI-6).
 * @property feelingScale how strongly the sentence's own [Feeling] is heard: 0 none, 1 as [FeelingEffect] says.
 */
data class DeliveryProfile(
    val rate: Float = 1f,
    val dialogueRate: Float = 1f,
    val pauseScale: Float = 1f,
    val headingPauseScale: Float = 1f,
    val clauseScale: Float = 1f,
    val feelingScale: Float = 0f,
) {
    init {
        val scales = listOf(rate, dialogueRate, pauseScale, headingPauseScale, clauseScale)
        require(scales.all { it > 0f && it.isFinite() }) { "delivery multipliers must be > 0, were $scales" }
        require(feelingScale in 0f..MAX_FEELING) { "feelingScale must be in 0..$MAX_FEELING, was $feelingScale" }
    }

    companion object {
        const val MAX_FEELING = 2f
    }
}

/**
 * What one [Feeling] does at full strength: [rate] and [pauseScale] are multipliers, [gainDb] is added loudness
 * (negative is softer), [semitones] moves the voice's pitch (ADR-076: sad lower, lively and surprised higher). Speed
 * and loudness alone were too small to hear; pitch is what a listener hears first. Each stays well inside the range
 * where a voice still sounds like itself.
 */
data class FeelingEffect(
    val rate: Float = 1f,
    val gainDb: Float = 0f,
    val pauseScale: Float = 1f,
    val semitones: Float = 0f,
) {
    init {
        require(rate > 0f && rate.isFinite() && pauseScale > 0f && pauseScale.isFinite()) { "bad effect $this" }
        require(gainDb.isFinite() && semitones.isFinite()) { "gainDb and semitones must be finite: $this" }
    }
}

/**
 * Rates are per 1,000 words of the pages read so far.
 *
 * @property minWords the intent is not told before this many words were read; until then the book reads as
 *   [ContentIntent.GENERAL].
 * @property settleWords after this many words the intent is kept for the rest of the session, so a chapter of
 *   dialogue in a report or a table in a novel does not change how the whole book sounds.
 * @property poemLineWords a poem's lines average at most this many words,
 * @property poemShortLineShare and at least this share of its lines are clearly shorter than the page's longest,
 *   with little dialogue (a novel's short dialogue lines are ragged too),
 * @property minPoemLines over at least this many lines (a page of real verse, not one short paragraph).
 * @property shortLine a line shorter than this share of the page's longest line is short.
 * @property scriptureRate words of worship ("Lord", "thee", "भगवान", "॥", "ঈশ্বর") at least this often,
 * @property verseRate or verse numbers ("3:16", "॥ १२ ॥") at least this often.
 * @property reportRate official vocabulary ("Ministry", "अधिसूचना", "সরকার") at least this often,
 * @property reportDigitShare or at least this share of letters and digits are digits (lists of figures).
 * @property newsRate datelines, agencies and reporting phrases ("(PTI)", "said on Monday", "ने बताया") this often.
 * @property studyStructureRate headings and list items at least this often,
 * @property studyCueRate with words of teaching ("Example", "उदाहरण", "উদাহরণ") at least this often,
 * @property studyCitationRate or citations ("[4]", "(Kot, 1986)") at least this often,
 * @property studyFurnitureRate or captions, equations and footnotes at least this often (a paper's furniture).
 * @property storyDialogueShare a story has at least this share of its sentences with quoted speech,
 * @property storyVerbRate or speech verbs ("said", "कहा", "বলল") at least this often,
 * @property storyOpenerRate or story openers ("Once upon a time", "एक बार की बात", "অনেক দিন আগে") this often.
 * @property childSentenceWords a story whose sentences average at most this many words is a storybook.
 * @property profiles each intent's delivery; every intent needs one.
 * @property effects each feeling's effect; every feeling needs one.
 * @property minRate the slowest a delivery may make the voice, whatever the multipliers.
 * @property maxGainDb the loudest a feeling may make a sentence; the shaper's soft limiter catches the peaks.
 * @property maxSemitones the furthest a feeling may move the pitch, either way, after the profile's strength: past
 *   about 2.5 semitones (15 %) a voice stops sounding like itself (ADR-071).
 * @property openEndWords a prose sentence of at least this many words that breaks off (a page or column cut it,
 *   "…in old times must") is read as unfinished: rising, not falling, and with only [openEndPauseScale] of its pause.
 */
data class DeliveryConfig(
    val minWords: Int = 120,
    val settleWords: Int = 1_500,
    val poemLineWords: Float = 8.5f,
    val poemShortLineShare: Float = 0.45f,
    val minPoemLines: Int = 10,
    val shortLine: Float = 0.8f,
    val scriptureRate: Float = 8f,
    val verseRate: Float = 6f,
    val reportRate: Float = 8f,
    val reportDigitShare: Float = 0.2f,
    val newsRate: Float = 3f,
    val studyStructureRate: Float = 6f,
    val studyCueRate: Float = 1f,
    val studyCitationRate: Float = 2f,
    val studyFurnitureRate: Float = 3.5f,
    val storyDialogueShare: Float = 0.12f,
    val storyVerbRate: Float = 3f,
    val storyOpenerRate: Float = 0.5f,
    val childSentenceWords: Float = 10f,
    val profiles: Map<ContentIntent, DeliveryProfile> = DEFAULT_PROFILES,
    val effects: Map<Feeling, FeelingEffect> = DEFAULT_EFFECTS,
    val minRate: Float = 0.75f,
    val maxGainDb: Float = 3f,
    val maxSemitones: Float = 2.5f,
    val openEndWords: Int = 4,
    val openEndPauseScale: Float = 0.2f,
) {
    init {
        require(minWords >= 0 && settleWords >= minWords) { "need 0 <= minWords <= settleWords" }
        require(minPoemLines >= 1 && poemLineWords > 0f) { "poem thresholds must be positive" }
        val shares = listOf(poemShortLineShare, shortLine, reportDigitShare, storyDialogueShare)
        require(shares.all { it in 0f..1f }) { "delivery shares must be in 0..1, were $shares" }
        val rates = listOf(scriptureRate, verseRate, reportRate, newsRate, studyStructureRate, studyCueRate)
        val more = listOf(studyCitationRate, studyFurnitureRate, storyVerbRate, storyOpenerRate)
        require((rates + more).all { it >= 0f }) {
            "delivery rates must be >= 0"
        }
        require(childSentenceWords > 0f) { "childSentenceWords must be > 0, was $childSentenceWords" }
        require(profiles.keys == ContentIntent.entries.toSet()) { "every intent needs a delivery profile" }
        require(effects.keys == Feeling.entries.toSet()) { "every feeling needs an effect" }
        require(minRate > 0f && maxGainDb >= 0f) { "minRate must be > 0 and maxGainDb >= 0" }
        require(maxSemitones in 0f..MAX_SEMITONES) { "maxSemitones must be in 0..$MAX_SEMITONES, was $maxSemitones" }
        require(openEndWords >= 1 && openEndPauseScale in 0f..1f) { "need openEndWords >= 1, pause scale in 0..1" }
    }

    fun profile(intent: ContentIntent): DeliveryProfile = profiles.getValue(intent)

    fun effect(feeling: Feeling): FeelingEffect = effects.getValue(feeling)

    private companion object {
        /** An octave: no feeling is read further from the voice's own pitch. */
        const val MAX_SEMITONES = 12f

        // Starting points from audiobook practice (children's narration ~10 % slower, news ~5 % brisker, verse with
        // long line pauses); tune by ear on the phone. GENERAL is today's reading plus gentle feelings.
        val DEFAULT_PROFILES = mapOf(
            ContentIntent.STORY to DeliveryProfile(
                rate = 0.98f,
                dialogueRate = 1.02f,
                pauseScale = 1.15f,
                feelingScale = 1f,
            ),
            ContentIntent.CHILDREN to DeliveryProfile(
                rate = 0.9f,
                dialogueRate = 0.94f,
                pauseScale = 1.35f,
                clauseScale = 1.2f,
                feelingScale = 1.25f,
            ),
            ContentIntent.POETRY to
                DeliveryProfile(rate = 0.9f, dialogueRate = 0.9f, pauseScale = 1.5f, clauseScale = 1.4f),
            ContentIntent.SCRIPTURE to DeliveryProfile(
                rate = 0.92f,
                dialogueRate = 0.92f,
                pauseScale = 1.4f,
                clauseScale = 1.25f,
            ),
            ContentIntent.NEWS to DeliveryProfile(rate = 1.04f, dialogueRate = 1.04f, pauseScale = 0.9f),
            ContentIntent.REPORT to DeliveryProfile(rate = 0.97f, dialogueRate = 0.97f, headingPauseScale = 1.2f),
            ContentIntent.STUDY to DeliveryProfile(
                rate = 0.96f,
                dialogueRate = 0.96f,
                pauseScale = 1.1f,
                headingPauseScale = 1.3f,
                clauseScale = 1.1f,
            ),
            ContentIntent.GENERAL to DeliveryProfile(feelingScale = 0.6f),
        )

        // ADR-076: tuned by ear on rendered story passages (samples in the project's audora/samples/character-voices).
        val DEFAULT_EFFECTS = mapOf(
            Feeling.LOUD to FeelingEffect(rate = 1.05f, gainDb = 3f, semitones = 1f),
            Feeling.SOFT to FeelingEffect(rate = 0.93f, gainDb = -6f, pauseScale = 1.2f, semitones = -0.7f),
            Feeling.SAD to FeelingEffect(rate = 0.9f, gainDb = -3f, pauseScale = 1.4f, semitones = -1.5f),
            Feeling.SCARED to FeelingEffect(rate = 1.07f, gainDb = -2f, semitones = 1.5f),
            Feeling.SURPRISED to FeelingEffect(rate = 1f, gainDb = 2f, pauseScale = 1.1f, semitones = 2f),
            Feeling.LIVELY to FeelingEffect(rate = 1.06f, gainDb = 2f, semitones = 2f),
            Feeling.EMPHATIC to FeelingEffect(rate = 1.03f, gainDb = 2f, semitones = 1f),
            Feeling.TRAILING to FeelingEffect(rate = 0.96f, pauseScale = 1.5f, semitones = -0.5f),
            Feeling.ASKING to FeelingEffect(pauseScale = 1.15f, semitones = 0.5f),
            Feeling.NEUTRAL to FeelingEffect(),
        )
    }
}
