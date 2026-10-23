// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceNotesModel.kt
// Purpose: Voice notes on a sentence (plan item 32): record, list, play and delete them for the open book.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.VoiceNote
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.playback.notes.VoiceNoteAudio
import io.github.skm0078.audora.playback.notes.VoiceNoteError
import io.github.skm0078.audora.playback.tutor.MicrophoneGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoiceNoteFailure { MICROPHONE, NO_MICROPHONE_PERMISSION, TOO_SHORT, NOT_SAVED, NOT_PLAYED }

/**
 * @property notes this book's voice notes in reading order.
 * @property recordingFor the sentence being recorded on, while the recording dialog shows.
 * @property playing the note being played.
 * @property failure the last thing that went wrong; the screen says so once.
 */
data class VoiceNotesState(
    val notes: List<VoiceNote> = emptyList(),
    val recordingFor: ReadingUnit? = null,
    val playing: Long? = null,
    val failure: VoiceNoteFailure? = null,
)

interface VoiceNotesActions {
    /** Starts recording on [unit]; the caller already holds RECORD_AUDIO. Reading pauses first. */
    fun record(unit: ReadingUnit) = Unit

    fun finishRecording() = Unit

    fun cancelRecording() = Unit

    fun play(note: VoiceNote) = Unit

    fun stopPlaying() = Unit

    fun delete(note: VoiceNote) = Unit

    fun clearFailure() = Unit

    /** How long the recording has run, for the dialog's clock. */
    fun recordingMs(): Long = 0L

    /** The microphone permission was refused. */
    fun permissionRefused() = Unit

    companion object {
        val None = object : VoiceNotesActions {}
    }
}

/** What the notes overlays need from voice notes; [None] where they are not wired (previews). */
class VoiceNotesBinding(
    val state: VoiceNotesState,
    val actions: VoiceNotesActions,
    /** Asks for the microphone when needed, then records on the sentence. */
    val askToRecord: (ReadingUnit) -> Unit,
) {
    val enabled: Boolean get() = this !== None

    companion object {
        val None = VoiceNotesBinding(VoiceNotesState(), VoiceNotesActions.None) {}
    }
}

/**
 * Plain state holder, one per open book. Main thread. [scope] should outlive the screen (activity-wide): a recording
 * saved as the reader closes must still reach the database. The recordings are the listener's voice: never logged.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One function per control; each parameter a seam tests replace.
class VoiceNotesModel(
    private val bookId: BookId,
    private val store: VoiceNoteStore,
    private val audio: VoiceNoteAudio,
    private val scope: CoroutineScope,
    private val anchorOf: (ReadingUnit) -> ResumeAnchor,
    private val pauseReading: () -> Unit,
    private val gate: MicrophoneGate = MicrophoneGate.shared,
) : VoiceNotesActions {
    private val mutableState = MutableStateFlow(VoiceNotesState())
    val state: StateFlow<VoiceNotesState> = mutableState.asStateFlow()
    private val logContext = "book=${bookId.logLabel}"
    private var recording: VoiceNoteAudio.Recording? = null
    private var starting: Job? = null
    private var hold: AutoCloseable? = null
    private var playing: VoiceNoteAudio.Playing? = null
    private var observer: Job? = null

    fun start() {
        if (observer != null) return
        observer = scope.launch {
            store.observe(bookId).collect { list -> mutableState.update { it.copy(notes = list) } }
        }
    }

    override fun record(unit: ReadingUnit) {
        if (recording != null || starting?.isActive == true) return
        stopPlaying()
        pauseReading()
        starting = scope.launch {
            // Voice commands pause while the note records; taken where cancelling cannot drop it.
            val held = withContext(NonCancellable) { gate.hold(MICROPHONE_REASON) }
            // Only this start: after a cancel and a quick re-tap, a newer start is in the field.
            if (starting === coroutineContext.job) starting = null
            if (!isActive) {
                held.close()
                return@launch
            }
            when (val begun = audio.record(onLimit = ::finishRecording)) {
                is Outcome.Success -> {
                    recording = begun.value
                    hold = held
                    mutableState.update { it.copy(recordingFor = unit) }
                }

                is Outcome.Failure -> {
                    held.close()
                    mutableState.update { it.copy(failure = VoiceNoteFailure.MICROPHONE) }
                }
            }
        }
    }

    override fun finishRecording() {
        val current = recording ?: return
        val unit = mutableState.value.recordingFor ?: return
        recording = null
        mutableState.update { it.copy(recordingFor = null) }
        val finished = current.finish()
        releaseMicrophone()
        when (finished) {
            is Outcome.Success -> save(VoiceNote(0, anchorOf(unit), current.fileName, finished.value))
            is Outcome.Failure -> mutableState.update { it.copy(failure = failureOf(finished.error)) }
        }
    }

    override fun cancelRecording() {
        starting?.cancel()
        starting = null
        recording?.cancel()
        recording = null
        releaseMicrophone()
        mutableState.update { it.copy(recordingFor = null) }
    }

    override fun recordingMs(): Long = recording?.elapsedMs() ?: 0L

    override fun permissionRefused() = mutableState.update {
        it.copy(failure = VoiceNoteFailure.NO_MICROPHONE_PERMISSION)
    }

    override fun play(note: VoiceNote) {
        stopPlaying()
        pauseReading()
        when (val begun = audio.play(note.fileName) { onPlayed(note.id) }) {
            is Outcome.Success -> {
                playing = begun.value
                mutableState.update { it.copy(playing = note.id) }
            }

            is Outcome.Failure -> mutableState.update { it.copy(failure = VoiceNoteFailure.NOT_PLAYED) }
        }
    }

    override fun stopPlaying() {
        playing?.stop()
        playing = null
        mutableState.update { it.copy(playing = null) }
    }

    override fun delete(note: VoiceNote) {
        if (mutableState.value.playing == note.id) stopPlaying()
        scope.launch {
            when (val deleted = store.delete(note.id)) {
                is Outcome.Success -> {
                    audio.delete(note.fileName)
                    AudoraLog.i(TAG, "Voice note deleted, ${note.logLabel}")
                }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Voice note not deleted, $logContext: ${deleted.error.detail}")
                    mutableState.update { it.copy(failure = VoiceNoteFailure.NOT_SAVED) }
                }
            }
        }
    }

    override fun clearFailure() = mutableState.update { it.copy(failure = null) }

    /** The screen is closing: a recording in progress is saved, playback stops. */
    fun close() {
        starting?.cancel()
        finishRecording()
        stopPlaying()
        observer?.cancel()
    }

    private fun save(note: VoiceNote) {
        scope.launch {
            when (val added = store.add(note)) {
                is Outcome.Success -> AudoraLog.i(TAG, "Voice note saved, ${note.copy(id = added.value).logLabel}")

                is Outcome.Failure -> {
                    audio.delete(note.fileName)
                    AudoraLog.w(TAG, "Voice note not saved, $logContext: ${added.error.detail}")
                    mutableState.update { it.copy(failure = VoiceNoteFailure.NOT_SAVED) }
                }
            }
        }
    }

    private fun releaseMicrophone() {
        hold?.close()
        hold = null
    }

    private fun onPlayed(id: Long) {
        if (mutableState.value.playing == id) stopPlaying()
    }

    private fun failureOf(error: VoiceNoteError) = when (error) {
        VoiceNoteError.TooShort -> VoiceNoteFailure.TOO_SHORT
        is VoiceNoteError.Recorder -> VoiceNoteFailure.MICROPHONE
        is VoiceNoteError.Player -> VoiceNoteFailure.NOT_PLAYED
    }

    private companion object {
        const val TAG = "VoiceNotes"
        const val MICROPHONE_REASON = "voice note"
    }
}
