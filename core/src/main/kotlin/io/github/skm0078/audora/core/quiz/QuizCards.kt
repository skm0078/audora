// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizCards.kt
// Purpose: Turns highlights into fill-in-the-blank cards with answer choices, offline and deterministic (item 70).

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.dictionary.DictionaryLanguage
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.Highlight
import kotlin.random.Random

/**
 * One card: [shown] is the highlight with [answer] blanked out as [BLANK]; [spoken] says "blank" there instead.
 * [choices] holds the answer and its distractors in a fixed shuffled order, or is empty when there were too few
 * words of the same language to offer any (the card is then answered by revealing it).
 */
data class QuizCard(
    val highlightId: Long,
    val shown: String,
    val spoken: String,
    val answer: String,
    val choices: List<String>,
    val lang: Lang,
) {
    /** Log form: identifiers only, never the listener's highlighted words. */
    val logLabel: String get() = "card=$highlightId lang=${lang.name} choices=${choices.size}"

    companion object {
        const val BLANK = "_____"
    }
}

/**
 * The blank is the highlight's rarest word across all the listener's highlights (ties: the longest, then the first),
 * since a rare word is usually the one worth remembering and "the" never is. Distractors are words of the same
 * language from other highlights, nearest in length first, so they look like plausible answers. Everything is
 * seeded by the highlight's id: the same highlights always give the same cards.
 *
 * Words are runs of letters and combining marks, so Hindi and Bengali vowel signs stay inside their word.
 */
class QuizCards(private val config: QuizConfig = QuizConfig()) {
    fun build(highlights: List<Highlight>, spokenBlank: (Lang) -> String): List<QuizCard> {
        val words = highlights.associate { it.id to wordsOf(it.quote) }
        val counts = words.values.flatten().groupingBy { it.value.lowercase() }.eachCount()
        return highlights.mapNotNull { highlight ->
            val candidates = words.getValue(highlight.id).filter { it.value.codePointCount() >= config.minWordChars }
            val blank = candidates.minWithOrNull(
                compareBy<MatchResult> { counts.getValue(it.value.lowercase()) }
                    .thenByDescending { it.value.codePointCount() }
                    .thenBy { it.range.first },
            ) ?: return@mapNotNull null
            cardOf(highlight, blank, words, spokenBlank)
        }
    }

    private fun cardOf(
        highlight: Highlight,
        blank: MatchResult,
        words: Map<Long, List<MatchResult>>,
        spokenBlank: (Lang) -> String,
    ): QuizCard {
        val answer = blank.value
        val lang = DictionaryLanguage.of(answer)
        val (before, after) = window(highlight.quote, blank.range)
        val random = Random(highlight.id)
        val others = words.filterKeys { it != highlight.id }.values.flatten()
            .map { it.value }
            .filter { it.codePointCount() >= config.minWordChars && DictionaryLanguage.of(it) == lang }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() != answer.lowercase() }
            .shuffled(random)
            .sortedBy { kotlin.math.abs(it.codePointCount() - answer.codePointCount()) }
            .take(config.choices - 1)
        val choices = if (others.size == config.choices - 1) (others + answer).shuffled(random) else emptyList()
        return QuizCard(
            highlightId = highlight.id,
            shown = before + QuizCard.BLANK + after,
            spoken = before + spokenBlank(lang) + after,
            answer = answer,
            choices = choices,
            lang = lang,
        )
    }

    /** The text before and after [blank], cut with an ellipsis to fit [QuizConfig.maxPromptChars] around it. */
    private fun window(quote: String, blank: IntRange): Pair<String, String> {
        val room = (config.maxPromptChars - blank.count()).coerceAtLeast(0) / 2
        // Cut at a space, so no word is shown half.
        val start = (blank.first - room).coerceAtLeast(0).let { cut ->
            if (cut == 0) cut else quote.indexOf(' ', cut).takeIf { it in cut until blank.first }?.plus(1) ?: cut
        }
        val end = (blank.last + 1 + room).coerceAtMost(quote.length).let { cut ->
            if (cut == quote.length) cut else quote.lastIndexOf(' ', cut).takeIf { it > blank.last } ?: cut
        }
        val before = (if (start > 0) ELLIPSIS else "") + quote.substring(start, blank.first).trimStart()
        val after = quote.substring(blank.last + 1, end).trimEnd() + (if (end < quote.length) ELLIPSIS else "")
        return before to after
    }

    private companion object {
        val WORD = Regex("""\p{L}[\p{L}\p{M}]*""")
        const val ELLIPSIS = "…"

        fun wordsOf(text: String): List<MatchResult> = WORD.findAll(text).toList()

        fun String.codePointCount(): Int = codePointCount(0, length)
    }
}
