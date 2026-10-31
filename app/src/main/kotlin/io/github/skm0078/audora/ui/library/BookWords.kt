// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookWords.kt
// Purpose: "Words you looked up" (personal touch 16): the book menu's page of a book's looked-up words with their
//          meanings, and "Quiz me on 5 words", both shown in the menu itself so nothing stacks on it.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.quiz.QuizRound
import io.github.skm0078.audora.core.words.LookedUpWord
import io.github.skm0078.audora.core.words.WordQuiz
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.quiz.QuizSpeaker
import io.github.skm0078.audora.ui.quiz.QuizWording
import io.github.skm0078.audora.ui.quiz.RoundBody
import io.github.skm0078.audora.ui.quiz.WordQuizModel
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every book's looked-up words, newest first, read from the file once (off the main thread). */
@Composable
internal fun rememberLookedUpWords(): List<LookedUpWord> {
    val context = LocalContext.current.applicationContext
    val words = remember(context) { LibraryEntryPoint.of(context).lookedUpWords() }
    LaunchedEffect(words) { withContext(Dispatchers.IO) { words.load() } }
    return words.words.collectAsState().value
}

/** The words page or the quiz, as [page] says; [onPage] moves between them and back to the menu. */
@Composable
internal fun BookWordsPages(
    page: BookMenuPage,
    words: List<LookedUpWord>,
    pool: List<LookedUpWord>,
    onPage: (BookMenuPage) -> Unit,
) {
    when (page) {
        BookMenuPage.QUIZ -> BookWordsQuiz(words, pool) { onPage(BookMenuPage.WORDS) }
        else -> BookWordsPage(words, onBack = { onPage(BookMenuPage.MENU) }, onQuiz = { onPage(BookMenuPage.QUIZ) })
    }
}

/** The words page (the menu offers it once the book has a word): each word, its page and meaning, and the quiz. */
@Composable
private fun BookWordsPage(words: List<LookedUpWord>, onBack: () -> Unit, onQuiz: () -> Unit) {
    val asked = minOf(words.size, WordQuiz.DEFAULT_COUNT)
    Column(PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        PageTitle(stringResource(R.string.book_words_title), onBack)
        Text(
            pluralStringResource(R.plurals.book_words_count, words.size, words.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onQuiz, Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.book_words_quiz, asked, asked))
        }
        words.forEach { word ->
            HorizontalDivider()
            WordLine(word)
        }
    }
}

@Composable
private fun WordLine(word: LookedUpWord) {
    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.word, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.book_words_page, word.page + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(word.meaning, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The quiz on the newest of [words], its distractors drawn from [pool] too. Its voice is freed when the page goes.
 * Done, Back and the title's back arrow all return to the words.
 */
@Composable
private fun BookWordsQuiz(words: List<LookedUpWord>, pool: List<LookedUpWord>, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val speaker = remember { QuizSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    // Built once for the page: a word looked up meanwhile does not reshuffle the round being answered.
    val model = remember { WordQuizModel(WordQuiz.cards(words, pool), scope, speaker::speak).apply { start() } }
    val round by model.round.collectAsState()
    Column(PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        PageTitle(stringResource(R.string.book_words_quiz_title), onBack)
        if (!round.finished) {
            Text(stringResource(R.string.book_words_quiz_ask), style = MaterialTheme.typography.labelLarge)
        }
        RoundBody(round, model, WORD_QUIZ)
        QuizSteps(round, model, onBack)
    }
}

/** Hear again while a card is up, and the one step forward: show the meaning, next, or done. */
@Composable
private fun QuizSteps(round: QuizRound, model: WordQuizModel, onDone: () -> Unit) {
    val card = round.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End)) {
        if (card != null) {
            TextButton(onClick = model::hearAgain) { Text(stringResource(R.string.quiz_hear_again)) }
        }
        when {
            card != null && round.verdict != null ->
                Button(onClick = model::next) { Text(stringResource(R.string.quiz_next)) }

            card != null && card.choices.isEmpty() && !round.revealed ->
                Button(onClick = model::reveal) { Text(stringResource(R.string.quiz_show_answer)) }

            card == null -> Button(onClick = onDone) { Text(stringResource(R.string.quiz_done)) }

            else -> Unit
        }
    }
}

@Composable
private fun PageTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

private val PAGE_PADDING = Modifier.padding(horizontal = Dimens.ScreenPadding)

private val WORD_QUIZ = QuizWording(
    score = R.string.book_words_quiz_score,
    answerWas = R.string.book_words_quiz_means,
    right = R.string.book_words_quiz_right,
    wrong = R.string.book_words_quiz_wrong,
)
