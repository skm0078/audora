// File: app/src/main/kotlin/io/github/skm0078/audora/ui/tutor/TutorModel.kt
// Purpose: Runs "Read aloud to me": listens while the reader reads the shown page and marks each word (item 67).

package io.github.skm0078.audora.ui.tutor

import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.tutor.PageWords
import io.github.skm0078.audora.core.tutor.ReadAlong
import io.github.skm0078.audora.core.tutor.TutorReport
import io.github.skm0078.audora.core.tutor.WordMark
import io.github.skm0078.audora.playback.tutor.TutorError
import io.github.skm0078.audora.playback.tutor.TutorSession
import io.github.skm0078.audora.speech.listen.Listener
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The shown page as the tutor reads it: its sentences and the language most of them are in. */
data class TutorPage(val page: Int, val sentences: List<String>, val lang: Lang) {
    companion object {
        /** Sentences with text (a zero-length table summary has none); null when the page has none at all. */
        fun of(page: Int, units: List<ReadingUnit>): TutorPage? {
            val spoken = units.filter { it.text.isNotBlank() }
            val lang = spoken.groupingBy { it.lang }.eachCount().maxByOrNull { it.value }?.key ?: return null
            return TutorPage(page, spoken.map { it.text }, lang)
        }
    }
}

/**
 * @property along every word's mark and where the reader is.
 * @property preparing the speech model is loading (a second or two) before the microphone opens.
 * @property listening the microphone is open.
 * @property failure why the last run stopped early, until the next one starts.
 */
data class TutorState(
    val page: TutorPage,
    val along: ReadAlong,
    val preparing: Boolean = false,
    val listening: Boolean = false,
    val failure: TutorError? = null,
) {
    /** Something has been heard on this page. */
    val started: Boolean get() = along.marks.any { it != WordMark.UNREAD }

    /** What to show once listening stops; null while listening or before anything was heard. */
    val report: TutorReport? get() = if (listening || !started) null else along.report()

    /** What to keep across a rotation or process death (the settled marks and place). */
    val saved: String get() = along.saved()

    /** A page word as printed, for the "words to check" list and for hearing it. */
    fun wordText(index: Int): String = along.words[index].let { page.sentences[it.unit].substring(it.start, it.end) }
}

/**
 * The tutor's state and actions; the screen only draws [state]. [newSession] makes one listening run (microphone, gate,
 * decoding); [openListener] loads the page language's model; [pause] stops reading aloud first, since the microphone
 * would hear it; [speak] says one word in the book's voice ("hear it"), never while listening, and [releaseVoice]
 * frees that voice before the speech model loads, so the two are never in memory together. [saved] is what
 * [TutorState.saved] kept before a rotation or process death. Main thread.
 */
@Suppress("LongParameterList") // Each is one seam the tests replace; none derivable from another.
class TutorModel(
    page: TutorPage,
    private val scope: CoroutineScope,
    private val newSession: () -> TutorSession,
    private val openListener: (Lang, Boolean) -> Listener?,
    private val pause: () -> Unit,
    private val speak: suspend (String, Lang) -> Boolean,
    private val config: TutorConfig = TutorConfig(),
    private val releaseVoice: () -> Unit = {},
    saved: String? = null,
) {
    private val mutableState = MutableStateFlow(
        TutorState(page, ReadAlong.resume(PageWords.of(page.sentences, page.lang), saved, config)),
    )
    private var run: Job? = null
    private var session: TutorSession? = null

    // Bumped by every start and start over: a cancelled run's late marks never land on a fresh page.
    private val generation = AtomicLong()

    val state: StateFlow<TutorState> = mutableState.asStateFlow()

    /** Starts listening, or carries on where the reader stopped; a read page starts over. */
    fun start() = begin(retry = false)

    /** *Try again* after a model that did not load: loads it even though the last load never returned. */
    fun retry() = begin(retry = true)

    private fun begin(retry: Boolean) {
        if (run?.isActive == true) return
        pause()
        releaseVoice()
        val current = mutableState.value
        val from = if (current.along.finished) fresh(current.page) else current.along
        val made = newSession().also { session = it }
        mutableState.update { it.copy(along = from, preparing = true, listening = true, failure = null) }
        AudoraLog.i(TAG, "Tutor listening, page=${current.page.page}, lang=${current.page.lang.bcp47Tag}")
        val previous = run
        val mine = generation.incrementAndGet()
        run = scope.launch {
            // A run being cancelled still holds the microphone and model for a moment; one at a time.
            previous?.join()
            val outcome = made.listen(from, { loadListener(retry) }) { changed ->
                if (generation.get() == mine) mutableState.update { it.copy(along = changed) }
            }
            mutableState.update { state ->
                when (outcome) {
                    is Outcome.Success -> state.copy(along = outcome.value, preparing = false, listening = false)
                    is Outcome.Failure -> state.copy(preparing = false, listening = false, failure = outcome.error)
                }
            }
        }
    }

    /** Stops after the stretch under way, which still counts. */
    fun stop() {
        session?.stop()
    }

    /** Stops listening at once and clears every mark. */
    fun startOver() {
        generation.incrementAndGet()
        run?.cancel()
        mutableState.update { it.copy(along = fresh(it.page), preparing = false, listening = false, failure = null) }
    }

    /** Says the page word at [index] (from the words to check). */
    fun hear(index: Int) {
        val current = mutableState.value
        if (current.listening || index !in current.along.words.indices) return
        scope.launch {
            if (!speak(current.wordText(index), current.page.lang)) AudoraLog.w(TAG, "Tutor word not spoken")
        }
    }

    /** The microphone permission was refused. */
    fun permissionRefused() {
        mutableState.update { it.copy(failure = TutorError.NO_PERMISSION) }
    }

    /** The screen closed: stops listening at once (nothing more is counted) and frees the model. */
    fun close() {
        run?.cancel()
    }

    private fun loadListener(retry: Boolean): Listener? = openListener(mutableState.value.page.lang, retry)
        .also { mutableState.update { it.copy(preparing = false) } }

    private fun fresh(page: TutorPage) = ReadAlong.start(PageWords.of(page.sentences, page.lang), config)

    private companion object {
        const val TAG = "Ui.Tutor"
    }
}
