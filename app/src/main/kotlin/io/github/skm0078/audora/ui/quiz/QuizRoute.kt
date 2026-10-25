// File: app/src/main/kotlin/io/github/skm0078/audora/ui/quiz/QuizRoute.kt
// Purpose: The "Quiz me" dialog: a highlight with a word blanked, read aloud, answered by a tap (plan item 70).

package io.github.skm0078.audora.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.quiz.QuizProgressStore
import io.github.skm0078.audora.core.quiz.QuizRound
import io.github.skm0078.audora.playback.quiz.QuizSpeaker
import io.github.skm0078.audora.ui.theme.Dimens
import java.io.File

/** While [open], runs one quiz over every book's highlights; its voices are freed when it closes. */
@Composable
fun QuizRoute(highlights: HighlightStore, open: Boolean, onClose: () -> Unit) {
    if (!open) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val speaker = remember { QuizSpeaker(context) }
    val model = remember {
        val store = QuizProgressStore(File(context.filesDir, QuizProgressStore.FILE_NAME))
        QuizModel(highlights, store, scope, speaker::speak).apply { start() }
    }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    val state by model.state.collectAsStateWithLifecycle()
    QuizDialog(state, model, onClose)
}

@Composable
private fun QuizDialog(state: QuizState, model: QuizModel, onClose: () -> Unit) {
    val round = (state as? QuizState.Asking)?.round
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.quiz_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                when (state) {
                    QuizState.Loading -> CircularProgressIndicator()
                    QuizState.NoCards -> Text(stringResource(R.string.quiz_no_cards))
                    QuizState.NothingDue -> Text(stringResource(R.string.quiz_nothing_due))
                    is QuizState.Asking -> RoundBody(state.round, model)
                }
            }
        },
        confirmButton = { ConfirmButton(state, round, model, onClose) },
        dismissButton = {
            if (round != null && !round.finished) {
                TextButton(onClick = model::hearAgain) { Text(stringResource(R.string.quiz_hear_again)) }
            }
        },
    )
}

/** The one forward step the quiz offers now: practise, next, show the answer, or done. */
@Composable
private fun ConfirmButton(state: QuizState, round: QuizRound?, model: QuizModel, onClose: () -> Unit) {
    val card = round?.current
    when {
        state == QuizState.NothingDue ->
            TextButton(onClick = model::practise) { Text(stringResource(R.string.quiz_practise)) }

        card != null && round?.verdict != null ->
            TextButton(onClick = model::next) { Text(stringResource(R.string.quiz_next)) }

        card != null && card.choices.isEmpty() && round?.revealed == false ->
            TextButton(onClick = model::reveal) { Text(stringResource(R.string.quiz_show_answer)) }

        else -> TextButton(onClick = onClose) { Text(stringResource(R.string.quiz_done)) }
    }
}

@Composable
private fun RoundBody(round: QuizRound, model: QuizModel) {
    val card = round.current
    if (card == null) {
        Text(stringResource(R.string.quiz_score, round.right, round.cards.size))
        return
    }
    Text(
        stringResource(R.string.quiz_progress, round.position + 1, round.cards.size),
        style = MaterialTheme.typography.labelMedium,
    )
    // TalkBack would read the blank as underscores; it reads the spoken form ("blank") instead.
    Text(
        card.shown,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { contentDescription = card.spoken },
    )
    card.choices.forEach { choice ->
        if (choice == round.picked) {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(choice) }
        } else {
            OutlinedButton(
                onClick = { model.choose(choice) },
                enabled = round.verdict == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(choice) }
        }
    }
    if (round.revealed && round.verdict == null) {
        Text(stringResource(R.string.quiz_answer_was, card.answer), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { model.grade(knewIt = true) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quiz_knew_it))
        }
        OutlinedButton(onClick = { model.grade(knewIt = false) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quiz_did_not))
        }
    }
    round.verdict?.let { right ->
        val verdict = if (right) R.string.quiz_right else R.string.quiz_wrong
        Text(
            stringResource(verdict, card.answer),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
