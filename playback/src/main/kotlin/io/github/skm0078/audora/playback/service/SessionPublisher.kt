// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionPublisher.kt
// Purpose: Publishes the book session's state as session extras and forwards reading/not-reading to diagnostics
//          and to the listening stats (CF-8).

package io.github.skm0078.audora.playback.service

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.session.MediaSession
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.session.SessionEvents
import io.github.skm0078.audora.playback.session.SessionState
import io.github.skm0078.audora.playback.stats.ListeningBridge
import io.github.skm0078.audora.speech.kws.NarratorEcho
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main thread. The sentence playing's timing ([timings], plan item 14) joins the extras when it starts, or when its
 * render finishes if it started first (the player waits for it then).
 */
@Suppress("LongParameterList") // The session, two bridges and what the extras show; the last six are optional.
class SessionPublisher(
    private val session: MediaSession,
    private val diagnostics: DiagnosticsBridge,
    private val resume: ResumeBridge,
    private val style: () -> SpeechStyle,
    private val sleepOptions: () -> SleepOptions = { SleepOptions() },
    private val bookSettings: () -> BookSettings? = { null },
    private val timings: SentenceTimings? = null,
    private val focusSound: () -> FocusSoundOptions = { FocusSoundOptions() },
    private val blend: () -> BlendAvailability = { BlendAvailability() },
    private val intent: () -> ContentIntent? = { null },
    private val listening: ListeningBridge? = null,
) : SessionEvents {
    private val main = Handler(Looper.getMainLooper())
    private var lastState: SessionState? = null
    private var playing: UnitKey? = null
    private var timing: SentenceTiming? = null

    init {
        timings?.onRecorded = { key -> main.post { if (key == playing) showTiming(key) } }
    }

    override fun onReading(bookId: String, page: Int) {
        diagnostics.reading(bookId, page)
        listening?.reading()
    }

    override fun onNotReading() {
        diagnostics.notReading()
        listening?.notReading()
        NarratorEcho.shared.stopped(SystemClock.elapsedRealtime())
        resume.flush()
    }

    override fun onSentenceStarted(bookId: BookId, unit: ReadingUnit) {
        resume.sentenceStarted(bookId, unit)
        // Voice commands (plan item 38) ignore the book's own voice while it says the app's name.
        NarratorEcho.shared.sentenceStarted(unit.text, SystemClock.elapsedRealtime())
        val key = UnitKey.of(unit)
        playing = key
        showTiming(key)
    }

    /**
     * The extras before any book plays: the speech style and sleep options, so a controller that connects to a fresh
     * service sees the saved style instead of defaults.
     */
    fun publishInitial() = publish(lastState ?: SessionState(null, null, SleepTimerMode.OFF, null))

    override fun onStateChanged(state: SessionState) {
        lastState = state
        publish(state)
    }

    private fun showTiming(key: UnitKey) {
        val now = timings?.of(key)
        if (now == timing) return
        timing = now
        lastState?.let(::publish)
    }

    private fun publish(state: SessionState) {
        session.setSessionExtras(
            Bundle().apply {
                state.bookId?.let { putString(PlaybackContract.SESSION_BOOK_ID, it) }
                if (state.sleepMode !=
                    SleepTimerMode.OFF
                ) {
                    putString(PlaybackContract.SESSION_SLEEP_MODE, state.sleepMode.name)
                }
                state.sleepEndsAtWallMillis?.let { putLong(PlaybackContract.SESSION_SLEEP_ENDS_AT, it) }
                if (state.hasChapters) putBoolean(PlaybackContract.SESSION_HAS_CHAPTERS, true)
                if (state.sleepChapters > 1) putInt(PlaybackContract.SESSION_SLEEP_CHAPTERS, state.sleepChapters)
                putString(PlaybackContract.SESSION_SLEEP_OPTIONS, sleepOptions().encode())
                putString(PlaybackContract.SESSION_FOCUS_SOUND, focusSound().encode())
                bookSettings()?.let {
                    putBoolean(PlaybackContract.SESSION_OWN_SETTINGS, true)
                    putString(PlaybackContract.SESSION_BOOK_VOICES, BookSettings.encodeVoices(it.voices))
                }
                state.undoTo?.let {
                    putInt(PlaybackContract.SESSION_UNDO_PAGE, it.page)
                    putInt(PlaybackContract.SESSION_UNDO_CHAR, it.charStart)
                }
                state.study?.let { putAll(PlaybackContract.studyExtras(it)) }
                state.error?.let {
                    putString(PlaybackContract.SESSION_ERROR, it.code.name)
                    putString(PlaybackContract.SESSION_ERROR_MESSAGE, it.message)
                }
                putSpeech(style())
                intent()?.let { putString(PlaybackContract.SESSION_READING_INTENT, it.name) }
                timing?.let { putString(PlaybackContract.SESSION_SENTENCE_TIMING, it.encode()) }
                putBlend(blend())
            },
        )
    }
}

private fun Bundle.putSpeech(speech: SpeechStyle) {
    putFloat(PlaybackContract.SESSION_SPEECH_RATE, speech.rate)
    putFloat(PlaybackContract.SESSION_PITCH, speech.pitch)
    putString(PlaybackContract.SESSION_PACE, speech.pace.name)
    putBoolean(PlaybackContract.SESSION_CLAUSE_PAUSES, speech.clausePauses)
    putString(PlaybackContract.SESSION_READING_MODES, speech.modes.encode())
    putString(PlaybackContract.SESSION_VOICE_SHAPING, speech.shaping.encode())
}

private fun Bundle.putBlend(now: BlendAvailability) {
    now.ownSpeaker?.let { putInt(PlaybackContract.SESSION_BLEND_OWN_SPEAKER, it) }
    now.neededBytes?.let { putLong(PlaybackContract.SESSION_BLEND_NEEDS_BYTES, it) }
    now.expressive?.let { putBoolean(PlaybackContract.SESSION_VOICE_EXPRESSIVE, it) }
}

/**
 * What the speech sheet's narrator blend (plan item 75) can do now: [ownSpeaker] the narrator's own Kokoro English
 * speaker (null when the narrator is another voice, which a blend leaves alone); [neededBytes] what the last blend
 * that could not be built needed free (0 when the voice refused it), null when it was built. [expressive]: whether a
 * voice reading now honours expressiveness (plan item 8), so the sheet shows its slider only then; null while none is
 * known.
 */
data class BlendAvailability(
    val ownSpeaker: Int? = null,
    val neededBytes: Long? = null,
    val expressive: Boolean? = null,
)

/**
 * P1-07's unfinished-session marker: "reading" while audio plays, "not reading" on pause, stop, end, error and
 * sleep timer. The tracker does blocking file I/O (atomic write + fsync), so calls run in order on one
 * background thread, never on the main thread.
 */
class DiagnosticsBridge(
    private val tracker: ReadingSessionTracker,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    fun reading(bookId: String, page: Int) {
        scope.launch(io) { report(tracker.onPlaybackActive(bookId, page), "active page=$page") }
    }

    fun notReading() {
        scope.launch(io) { report(tracker.onPlaybackInactive(), "inactive") }
    }

    private fun report(result: Outcome<Unit, *>, what: String) {
        if (result is Outcome.Failure) AudoraLog.w(TAG, "Session marker not written ($what): ${result.error}")
    }

    private companion object {
        const val TAG = "Playback.Diagnostics"
    }
}
