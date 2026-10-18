// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizCards.kt
// Purpose: Turns highlights into fill-in-the-blank cards with answer choices, offline and deterministic (item 70).

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.dictionary.DictionaryLanguage
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.text.TextCuts
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * One card: [shown] is the highlight with every occurrence of [answer] blanked out as [BLANK]; [spoken] says "blank"
 * there instead, and [line] is the passage with the answer in place. [choices] holds the answer and its distractors
 * in a fixed shuffled order, or is empty when there were too few words of the same language to offer any (the card is
 * then answered by revealing it).
 */
data class QuizCard(
    val highlightId: Long,
    val shown: String,
    val spoken: String,
    val answer: String,
    val choices: List<String>,
    val lang: Lang,
    val line: String = shown.replace(BLANK, answer),
) {
    /** Log form: identifiers only, never the listener's highlighted words. */
    val logLabel: String get() = "card=$highlightId lang=${lang.name} choices=${choices.size}"

    companion object {
        const val BLANK = "_____"
    }
}

/**
 * The blank is the highlight's rarest word across all the listener's highlights (ties: the most letters, then the
 * first), since a rare word is usually the one worth remembering and "the" never is. Distractors are words of the
 * same language from other highlights, nearest in letters first, so they look like plausible answers, cased like the
 * answer. Everything is seeded by the highlight's id: the same highlights always give the same cards.
 *
 * Words are runs of letters, combining marks and zero-width joiners, so Hindi and Bengali vowel signs, viramas and
 * joined forms (Bengali র‍্যা, khanda-ta written ত্‍) stay inside their word. Words are compared in NFC and lower case,
 * so two Unicode spellings of one word are one word. A token mixing scripts (usually an OCR misread) is never the
 * blank or a distractor.
 */
class QuizCards(private val config: QuizConfig = QuizConfig()) {
    /** Counts and word pools for [highlights], built once; cards are then made only for the ones asked. */
    fun deck(highlights: List<Highlight>): QuizDeck = QuizDeck(highlights, config)

    /** Every card [highlights] can give, in their order. */
    fun build(highlights: List<Highlight>, spokenBlank: (Lang) -> String): List<QuizCard> =
        deck(highlights).let { deck -> deck.ids.map { deck.card(it, spokenBlank) } }
}

/** One word of a highlight, with its comparison key and how many letters it has. */
private class Word(val match: MatchResult) {
    val value: String get() = match.value
    val key: String = keyOf(match.value)
    val letters: Int = match.value.codePoints().filter(Character::isLetter).count().toInt()
    val lang: Lang = DictionaryLanguage.of(match.value)
    val oneScript: Boolean = match.value.codePoints().filter(Character::isLetter)
        .mapToObj(Character.UnicodeScript::of).distinct().count() <= 1
}

/**
 * The listener's highlights, read once: word counts across all of them and one pool of distinct words per language.
 * [ids] lists the highlights that can make a card, in order; [card] makes one.
 */
class QuizDeck internal constructor(highlights: List<Highlight>, private val config: QuizConfig) {
    private val byId = highlights.associateBy { it.id }
    private val words: Map<Long, List<Word>> = highlights.associate { highlight ->
        highlight.id to WORD.findAll(highlight.quote).map(::Word).toList()
    }
    private val counts: Map<String, Int> = words.values.flatten().groupingBy { it.key }.eachCount()
    private val blanks: Map<Long, Word> = words.mapNotNull { (id, list) -> blankOf(list)?.let { id to it } }.toMap()
    private val pools: Map<Lang, List<Word>> = words.values.flatten()
        .filter(::canBeChoice)
        .distinctBy { it.key }
        .groupBy { it.lang }

    val ids: List<Long> = highlights.map { it.id }.filter { it in blanks }

    fun card(id: Long, spokenBlank: (Lang) -> String): QuizCard {
        val highlight = byId.getValue(id)
        val blank = blanks.getValue(id)
        val answer = blank.value
        val (before, after) = window(highlight.quote, blank.match.range)
        val random = Random(id)
        val own = words.getValue(id).mapTo(HashSet()) { it.key }
        val others = pools[blank.lang].orEmpty()
            .filter { it.key !in own }
            .shuffled(random)
            .sortedBy { abs(it.letters - blank.letters) }
            .take(config.choices - 1)
            .map { casedLike(it.value, answer) }
        val choices = if (others.size == config.choices - 1) (others + answer).shuffled(random) else emptyList()
        val shownBefore = blankOut(before, blank.key, QuizCard.BLANK)
        val shownAfter = blankOut(after, blank.key, QuizCard.BLANK)
        val spokenWord = spokenBlank(blank.lang)
        return QuizCard(
            highlightId = id,
            shown = shownBefore + QuizCard.BLANK + shownAfter,
            spoken = blankOut(before, blank.key, spokenWord) + spokenWord + blankOut(after, blank.key, spokenWord),
            answer = answer,
            choices = choices,
            lang = blank.lang,
            line = before + answer + after,
        )
    }

    private fun blankOf(list: List<Word>): Word? = list.filter(::canBeChoice).minWithOrNull(
        compareBy<Word> { counts.getValue(it.key) }
            .thenByDescending { it.letters }
            .thenBy { it.match.range.first },
    )

    private fun canBeChoice(word: Word): Boolean =
        word.oneScript && word.value.codePointCount(0, word.value.length) >= config.minWordChars

    /** The text before and after [blank], cut with an ellipsis to fit [QuizConfig.maxPromptChars] around it. */
    private fun window(quote: String, blank: IntRange): Pair<String, String> {
        val room = (config.maxPromptChars - blank.count()).coerceAtLeast(0) / 2
        // Cut at a space, so no word is shown half; without one, at a character boundary (never inside an aksara).
        val start = (blank.first - room).coerceAtLeast(0).let { cut ->
            if (cut == 0) {
                cut
            } else {
                quote.indexOf(' ', cut).takeIf { it in cut until blank.first }?.plus(1)
                    ?: TextCuts.boundaryAtOrAfter(quote, cut)
            }
        }
        val end = (blank.last + 1 + room).coerceAtMost(quote.length).let { cut ->
            if (cut == quote.length) {
                cut
            } else {
                quote.lastIndexOf(' ', cut).takeIf { it > blank.last } ?: TextCuts.boundaryAtOrBefore(quote, cut)
            }
        }
        val before = (if (start > 0) ELLIPSIS else "") + quote.substring(start, blank.first).trimStart()
        val after = quote.substring(blank.last + 1, end).trimEnd() + (if (end < quote.length) ELLIPSIS else "")
        return before to after
    }

    private companion object {
        const val ELLIPSIS = "…"

        /** Every word of [text] that is [key] in another spelling or case, replaced by [blank]. */
        fun blankOut(text: String, key: String, blank: String): String =
            WORD.replace(text) { if (keyOf(it.value) == key) blank else it.value }

        /** [word] with its first letter in [answer]'s case, so a capital never gives the answer away. */
        fun casedLike(word: String, answer: String): String {
            val first = answer.firstOrNull() ?: return word
            return when {
                first.isUpperCase() -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
                first.isLowerCase() -> word.replaceFirstChar { it.lowercase(Locale.ROOT) }
                else -> word
            }
        }
    }
}

private val WORD = Regex("""\p{L}[\p{L}\p{M}‌‍]*""")

private fun keyOf(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT)
