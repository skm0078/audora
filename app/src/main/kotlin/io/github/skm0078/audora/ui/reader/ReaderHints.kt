// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderHints.kt
// Purpose: The reader's first-use hints (ADR 2026-10-30-first-use-hints): the facts that call for one, read from what
//          the reader already holds, and where each hint's button leads.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.core.config.FirstUseHintsConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.core.hints.ReaderHintFacts
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.ui.hints.HintCard
import io.github.skm0078.audora.ui.hints.rememberHints
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.LibraryAction

/**
 * What the reader's hints lead to outside the reader. [onKeepOnPhone] is set only for a book read through Drive or
 * another app, which is also what calls for that hint.
 */
class ReaderHintLinks(
    val onLibraryAction: ((LibraryAction) -> Unit)? = null,
    val onKeepOnPhone: (() -> Unit)? = null,
) {
    companion object {
        val None = ReaderHintLinks()
    }
}

/** The facts for [state]'s hints. [pluggedIn] is read once as the book opens; it only adds the prepare-ahead hint. */
internal fun readerHintFacts(
    state: ReaderState,
    notes: NotesState,
    links: ReaderHintLinks,
    pluggedIn: Boolean,
    config: FirstUseHintsConfig = HINTS,
): ReaderHintFacts {
    val playback = state.playback
    val shaping = playback.voiceShaping
    val pageCount = state.pageCount
    val reading = state.readingPosition
    return ReaderHintFacts(
        cloudBook = links.onKeepOnPhone != null,
        fastWithoutSmartSpeed = playback.styleKnown && playback.speed > MAX_CLEAR_SPEED && !shaping.smartSpeed,
        storyWithoutCast = playback.readingIntent == ContentIntent.STORY &&
            shaping.dialogueVoices.isEmpty() && !shaping.characterVoices,
        hasHighlight = notes.highlights.isNotEmpty() && links.onLibraryAction != null,
        onLastPage = pageCount != null && reading?.page == pageCount - 1 && links.onLibraryAction != null,
        longHalfReadWhileCharging = pluggedIn && pageCount != null &&
            config.isLongAndHalfRead(reading?.page ?: state.page, pageCount),
    )
}

/**
 * What [hint]'s button does in the reader: a place in the Voice and speed sheet ([openSpeech]), a library tool, or the
 * job itself.
 */
internal fun actOnHint(
    hint: FirstUseHint,
    links: ReaderHintLinks,
    onPrepareAhead: () -> Unit,
    openSpeech: (SpeechSpot) -> Unit,
) {
    when (hint) {
        FirstUseHint.KEEP_OFFLINE -> links.onKeepOnPhone?.invoke()
        FirstUseHint.SMART_SPEED -> openSpeech(SpeechSpot.SOUND)
        FirstUseHint.CAST -> openSpeech(SpeechSpot.CAST)
        FirstUseHint.QUIZ -> links.onLibraryAction?.invoke(LibraryAction.QUIZ)
        FirstUseHint.FINISHED -> links.onLibraryAction?.invoke(LibraryAction.LISTENING_STATS)
        FirstUseHint.PREPARE_AHEAD -> onPrepareAhead()
        FirstUseHint.MORE_BOOKS -> Unit // The library's own hint.
    }
}

/**
 * The reader's hint now: [pending] when one would show, its card for the controls, and [speech] (the screen's own
 * request, or the sheet place a hint's Show me asked for).
 */
internal class ReaderHint(
    val pending: Boolean,
    val speech: SpeechRequest,
    private val card: (@Composable (Modifier) -> Unit)?,
) {
    /** The card, when the reader's one notice slot went to the hint. */
    fun cardFor(notice: ReaderNotice?): (@Composable (Modifier) -> Unit)? = card.takeIf { notice == ReaderNotice.HINT }
}

/** The hint for [state], if one applies; none in kids mode, which keeps to reading. */
@Composable
internal fun readerHint(
    state: ReaderState,
    notes: NotesState,
    links: ReaderHintLinks,
    actions: ReaderActions,
    speech: SpeechRequest,
): ReaderHint {
    val hints = rememberHints()
    val pluggedIn = rememberPluggedIn(state.bookId)
    var hintSpot by remember { mutableStateOf<SpeechSpot?>(null) }
    val open = state.document is DocumentState.Open && !LocalKidsMode.current
    val hint = if (open) hints.pick(readerHintFacts(state, notes, links, pluggedIn).candidates()) else null
    val request = hintSpot?.let { spot -> SpeechRequest(spot) { hintSpot = null } } ?: speech
    val card: (@Composable (Modifier) -> Unit)? = hint?.let { shown ->
        { modifier ->
            HintCard(shown, hints, onAct = {
                actOnHint(shown, links, actions::onPrepareAhead) { hintSpot = it }
            }, modifier)
        }
    }
    return ReaderHint(pending = hint != null, speech = request, card = card)
}

/** Whether the phone is on a charger as [id] opens: the sticky battery status, read once, with no receiver kept. */
@Composable
internal fun rememberPluggedIn(id: BookId): Boolean {
    val context = LocalContext.current
    return remember(id) { context.isPluggedIn() }
}

private fun Context.isPluggedIn(): Boolean {
    val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
}

private val HINTS = FirstUseHintsConfig()

/** Above this the voice is stretched and smart speed keeps it clear (the speech sheet says the same). */
private val MAX_CLEAR_SPEED = SpeechStyleConfig().maxModelRate
