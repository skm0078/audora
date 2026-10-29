// File: core/src/main/kotlin/io/github/skm0078/audora/core/words/WordQuiz.kt
// Purpose: "Quiz me on 5 words" (personal touch 16): a book's looked-up words as quiz cards, each asking which
//          meaning is the word's, offline and the same every time for the same words.

package io.github.skm0078.audora.core.words

import io.github.skm0078.audora.core.quiz.QuizCard
import kotlin.random.Random

/**
 * Cards for the newest [count] of [words] (one book's, newest first). Each shows the word and answers with its
 * meaning among three other words' meanings in the same language, the book's own first, then other books' from
 * [pool], in an order seeded by the word, so a card never shuffles under the listener between two looks. With too
 * few other meanings to choose from, a card has no choices and is answered by showing the meaning (as the highlight
 * quiz does). [QuizCard.line] is the sentence the word came from, which the voice reads once the card is answered.
 */
object WordQuiz {
    const val DEFAULT_COUNT = 5
    private const val OTHER_CHOICES = 3

    fun cards(
        words: List<LookedUpWord>,
        pool: List<LookedUpWord> = words,
        count: Int = DEFAULT_COUNT,
    ): List<QuizCard> = words.take(count).mapIndexed { index, word ->
        val others = (words.asSequence() + pool.asSequence())
            .filter { it.lang == word.lang && it.key != word.key }
            .map { it.meaning }
            .filter { it != word.meaning }
            .distinct()
            .take(OTHER_CHOICES)
            .toList()
        val random = Random(word.key.hashCode())
        val choices = if (others.size < OTHER_CHOICES) emptyList() else (others + word.meaning).shuffled(random)
        QuizCard(
            highlightId = index.toLong(),
            shown = word.word,
            spoken = word.word,
            answer = word.meaning,
            choices = choices,
            lang = word.lang,
            line = word.sentence,
        )
    }
}
