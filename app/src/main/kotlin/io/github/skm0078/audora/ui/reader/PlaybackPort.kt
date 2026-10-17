// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlaybackPort.kt
// Purpose: What the reader needs from the playback service, so the reader logic is tested without Media3.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.report.SoundedWrongReport
import kotlinx.coroutines.flow.StateFlow

/** Implemented over a Media3 `MediaController` by [MediaControllerPlaybackPort]; faked in tests. */
@Suppress("TooManyFunctions") // One per player control.
interface PlaybackPort {
    val state: StateFlow<PlaybackSnapshot>

    /** Replaces whatever is playing with this book from [position] and plays. Only ever on a user action (C7). */
    fun start(position: ReadingPosition, password: String?)

    fun play()

    fun pause()

    fun skipToNextSentence()

    fun skipToPreviousSentence()

    /** The voice's speaking rate (ADR-033): the voice itself speaks faster or slower; the audio is never stretched. */
    fun setSpeed(speed: Float)

    /** The silences between sentences (ADR-033). */
    fun setPace(pace: Pace)

    /** Pauses at dashes, colons, brackets and the like inside a sentence (RI-6). */
    fun setClausePauses(on: Boolean)

    /** What is read, announced or skipped: tables, footnotes, equations, captions, unreadable text (ADR-035). */
    fun setReadingModes(modes: ReadingModes)

    /** Even loudness, voice boost and pause trim (plan items 9, 10), from the next sentence rendered. */
    fun setVoiceShaping(shaping: VoiceShaping)

    fun setSleepTimer(setting: SleepTimerSetting)

    /** Shake to extend, fade-out and bedtime auto-on (plan item 37); the service keeps them. */
    fun setSleepOptions(options: SleepOptions)

    /** The open book keeps its own speed, pace and voices (plan item 4), or follows the global ones again. */
    fun setOwnSettings(on: Boolean)

    /** The voice reading [lang] in the open book, while it has its own settings (plan item 4). */
    fun setBookVoice(lang: Lang, voice: VoiceId)

    /** Back to where reading was before the last jump (plan item 78), playing or paused as now. */
    fun undoJump()

    /** "This sounded wrong" (RI-8b): the service logs it with the voice that reads its language. */
    fun reportSoundedWrong(report: SoundedWrongReport)

    /**
     * How far into the sentence playing the player is, in the clip's own time (plan item 14, word highlight); null
     * with nothing loaded. Cheap and current on every call (the controller runs the clock itself), so a highlight
     * can poll it once a frame and map it through [PlaybackSnapshot.sentenceTiming].
     */
    fun positionInSentenceMs(): Long? = null
}

/**
 * The player as the reader sees it.
 *
 * @property position the sentence being read (or about to be, while buffering); null when nothing is loaded
 *   or the item is not an Audora position.
 * @property wantsToPlay play was pressed and not paused since: true while buffering the next sentence too.
 * @property hasChapters the book being read has chapters, so [SleepTimerSetting.EndOfChapter] can be offered.
 * @property sleepOptions the sleep timer extras the service has saved.
 * @property ownSettings the book being read has its own speed, pace and voices; changes to them are its own.
 * @property bookVoices the voices the book being read keeps for itself, by language, while [ownSettings].
 * @property undoJumpTo where "Undo jump" goes back to in the book being read (plan item 78); null with nothing to undo.
 * @property sentenceTiming where the voice speaks in the sentence playing (plan item 14), once it is rendered; use it
 *   only while its page and char start match [position].
 * @property errorCode Media3's error code name of the last playback error, for the log and a generic message.
 */
data class PlaybackSnapshot(
    val connection: Connection,
    val position: ReadingPosition? = null,
    val wantsToPlay: Boolean = false,
    val buffering: Boolean = false,
    val speed: Float = SpeechStyle.NORMAL_RATE,
    val pace: Pace = Pace.NORMAL,
    val clausePauses: Boolean = true,
    val readingModes: ReadingModes = ReadingModes(),
    val voiceShaping: VoiceShaping = VoiceShaping(),
    val canSkipBack: Boolean = false,
    val canSkipForward: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val hasChapters: Boolean = false,
    val sleepOptions: SleepOptions = SleepOptions(),
    val ownSettings: Boolean = false,
    val bookVoices: Map<Lang, VoiceId> = emptyMap(),
    val undoJumpTo: ReadingPosition? = null,
    val sentenceTiming: SentenceTiming? = null,
    val errorCode: String? = null,
) {
    enum class Connection { CONNECTING, CONNECTED, UNAVAILABLE }

    companion object {
        val Connecting = PlaybackSnapshot(Connection.CONNECTING)
    }
}
