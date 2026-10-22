// File: app/src/main/kotlin/io/github/skm0078/audora/ui/tutor/TutorRoute.kt
// Purpose: The "Read aloud to me" dialog: the shown page with each word marked as it is read, then a summary (67).

package io.github.skm0078.audora.ui.tutor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.tutor.TutorReport
import io.github.skm0078.audora.core.tutor.WordMark
import io.github.skm0078.audora.playback.quiz.QuizSpeaker
import io.github.skm0078.audora.playback.tutor.AudioRecordMicrophone
import io.github.skm0078.audora.playback.tutor.MicrophoneGate
import io.github.skm0078.audora.playback.tutor.TutorError
import io.github.skm0078.audora.playback.tutor.TutorSession
import io.github.skm0078.audora.speech.listen.ListenKind
import io.github.skm0078.audora.speech.listen.ListenModels
import io.github.skm0078.audora.speech.listen.ListenPin
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.reader.KeepScreenOn
import kotlinx.coroutines.launch

/**
 * The tutor over [page] (null while the page loads, or when it has no text). [pause] stops reading aloud before the
 * microphone opens; [onNextPage] turns the reader's page, and the tutor follows. Closing stops listening and frees
 * the speech model and the "hear it" voice. The settled marks are saved, so rotation and process death keep them
 * (the dialog reopens through the reader's saved state).
 */
@Composable
fun TutorRoute(page: TutorPage?, loading: Boolean, pause: () -> Unit, onNextPage: (() -> Unit)?, onClose: () -> Unit) {
    if (page == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.tutor_title)) },
            text = { if (loading) CircularProgressIndicator() else Text(stringResource(R.string.tutor_no_text)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.tutor_close)) } },
        )
        return
    }
    val context = LocalContext.current.applicationContext
    val models = remember { ListenModels.get(context) }
    val speaker = remember { QuizSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    val scope = rememberCoroutineScope()
    var saved by rememberSaveable(page) { mutableStateOf<String?>(null) }
    val model = remember(page) {
        val config = TutorConfig()
        TutorModel(
            page = page,
            scope = scope,
            newSession = { TutorSession(AudioRecordMicrophone(context, config), MicrophoneGate.shared, config) },
            openListener = models::listener,
            pause = pause,
            speak = speaker::speak,
            config = config,
            releaseVoice = speaker::release,
            saved = saved,
        )
    }
    DisposableEffect(model) { onDispose { model.close() } }
    // Android silences the microphone for an app out of sight; listening stops (and counts) instead.
    LifecycleStartEffect(model) { onStopOrDispose { model.stop() } }
    val state by model.state.collectAsStateWithLifecycle()
    SideEffect { saved = state.saved }
    val download by remember(page.lang) { models.state(page.lang) }.collectAsStateWithLifecycle(initialValue = null)
    KeepScreenOn(state.listening)
    val listen = rememberWithMicrophone(model::start, model::permissionRefused)
    val ready = download is OcrModelsState.Ready
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.tutor_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ready) {
                    TutorBody(state, model, onNextPage)
                    FailureActions(state.failure, model::retry) { scope.launch { models.reinstall(page.lang) } }
                } else {
                    TutorDownloadOffer(page.lang, download, { models.download(page.lang) }) { models.cancel(page.lang) }
                }
            }
        },
        confirmButton = { if (ready) ListenButton(state, listen, model::stop) },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.tutor_close)) } },
    )
}

@Composable
private fun ListenButton(state: TutorState, listen: () -> Unit, stop: () -> Unit) {
    when {
        state.listening -> TextButton(onClick = stop) { Text(stringResource(R.string.tutor_stop)) }

        state.started && !state.along.finished ->
            TextButton(onClick = listen) { Text(stringResource(R.string.tutor_continue)) }

        else -> TextButton(onClick = listen) { Text(stringResource(R.string.tutor_start)) }
    }
}

@Composable
private fun TutorBody(state: TutorState, model: TutorModel, onNextPage: (() -> Unit)?) {
    val mode = when (ListenPin.of(state.page.lang).kind) {
        ListenKind.STREAMING_TRANSDUCER -> R.string.tutor_mode_words
        ListenKind.PHRASE_CTC -> R.string.tutor_mode_phrases
    }
    Text(stringResource(mode), style = MaterialTheme.typography.bodyMedium)
    Status(state)
    Text(markedPage(state), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.tutor_legend),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.report?.let { report -> Summary(state, report, model::hear) }
    if (state.started && !state.listening) {
        TextButton(onClick = model::startOver) { Text(stringResource(R.string.tutor_start_over)) }
    }
    if (state.along.finished && !state.listening && onNextPage != null) {
        OutlinedButton(onClick = onNextPage) { Text(stringResource(R.string.tutor_next_page)) }
    }
}

@Composable
private fun Status(state: TutorState) {
    val failure = state.failure
    val line = when {
        failure != null -> failureText(failure)
        state.preparing -> R.string.tutor_preparing
        state.listening -> R.string.tutor_listening
        else -> null
    } ?: return
    Text(
        stringResource(line),
        color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@StringRes
private fun failureText(failure: TutorError): Int = when (failure) {
    TutorError.MODEL_FAILED -> R.string.tutor_failed_model
    TutorError.NO_PERMISSION -> R.string.tutor_failed_permission
    TutorError.MICROPHONE_FAILED -> R.string.tutor_failed_microphone
}

/** The page with each judged word styled by its mark and the reader's place shaded. */
@Composable
private fun markedPage(state: TutorState): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val styles = WordStyles(
        read = SpanStyle(color = colors.primary),
        misread = SpanStyle(color = colors.error, textDecoration = TextDecoration.Underline),
        skipped = SpanStyle(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough),
        next = SpanStyle(background = colors.tertiaryContainer),
    )
    return remember(state, styles) { markedText(state, styles) }
}

private data class WordStyles(val read: SpanStyle, val misread: SpanStyle, val skipped: SpanStyle, val next: SpanStyle)

private fun markedText(state: TutorState, styles: WordStyles): AnnotatedString = buildAnnotatedString {
    val offsets = state.page.sentences.runningFold(0) { at, sentence -> at + sentence.length + 1 }
    append(state.page.sentences.joinToString(" "))
    state.along.words.forEachIndexed { index, word ->
        val start = offsets[word.unit] + word.start
        val end = offsets[word.unit] + word.end
        val style = when (state.along.marks[index]) {
            WordMark.READ -> styles.read
            WordMark.MISREAD -> styles.misread
            WordMark.SKIPPED -> styles.skipped
            WordMark.UNREAD, WordMark.PASSED -> null
        }
        style?.let { addStyle(it, start, end) }
        if (index == state.along.next && state.listening) addStyle(styles.next, start, end)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(state: TutorState, report: TutorReport, hear: (Int) -> Unit) {
    val percent = report.accuracyPercent
    Text(
        if (percent == null) {
            stringResource(R.string.tutor_score_none)
        } else {
            pluralStringResource(R.plurals.tutor_score, report.judged, report.read, report.judged, percent)
        },
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (report.skipped.isNotEmpty()) {
        Text(pluralStringResource(R.plurals.tutor_skipped, report.skipped.size, report.skipped.size))
    }
    if (report.toCheck.isEmpty()) return
    Text(stringResource(R.string.tutor_to_check))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        report.toCheck.forEach { index ->
            val word = state.wordText(index)
            val label = stringResource(R.string.tutor_hear_word, word)
            OutlinedButton(onClick = { hear(index) }, modifier = Modifier.semantics { contentDescription = label }) {
                Text(word)
            }
        }
    }
    Text(
        stringResource(R.string.tutor_check_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Runs [onGranted] once RECORD_AUDIO is held, asking first when it is not; a refusal calls [onRefused]. */
@Composable
private fun rememberWithMicrophone(onGranted: () -> Unit, onRefused: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Microphone permission granted=$granted")
        if (granted) onGranted() else onRefused()
    }
    return remember(launcher, onGranted) {
        {
            val held = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (held) onGranted() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private const val TAG = "Ui.Tutor"
