// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackContract.kt
// Purpose: The public contract between the playback service and any MediaController (reader screen, P1-05).

package io.github.skm0078.audora.playback.contract

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.playback.report.SoundedWrongReport
import io.github.skm0078.audora.playback.service.AudoraPlaybackService

/**
 * Everything the UI needs to drive playback, through a Media3 `MediaController` and nothing else.
 *
 * Standard player commands work as usual: play, pause, `seekToNextMediaItem` / `seekToPreviousMediaItem`
 * (skip a sentence), stop. Each playlist item is one sentence;
 * its `mediaMetadata.extras` carry where it is in the book (see the `ITEM_*` keys).
 *
 * Speed is the exception: `setPlaybackSpeed` stretches finished audio and smears words, so the reader sends
 * [SET_SPEECH_STYLE] instead. The voice itself speaks faster or slower, up to its model's cap, and the service sets
 * the player's speed to stretch only what is above that cap (ADR-033).
 *
 * What the player API cannot say goes through custom commands ([playFrom], [setVoice], [setSleepTimer]) and
 * the session extras (`controller.sessionExtras`, `Listener.onExtrasChanged`), keys `SESSION_*`.
 */
@Suppress("TooManyFunctions") // An args builder and a parser per custom command.
object PlaybackContract {
    private const val PREFIX = "io.github.skm0078.audora.playback"

    const val ACTION_PLAY_FROM = "$PREFIX.PLAY_FROM"
    const val ACTION_SET_VOICE = "$PREFIX.SET_VOICE"
    const val ACTION_SET_SLEEP_TIMER = "$PREFIX.SET_SLEEP_TIMER"
    const val ACTION_SET_SPEECH_STYLE = "$PREFIX.SET_SPEECH_STYLE"
    const val ACTION_REPORT_SOUNDED_WRONG = "$PREFIX.REPORT_SOUNDED_WRONG"
    const val ACTION_UNDO_JUMP = "$PREFIX.UNDO_JUMP"
    const val ACTION_SET_SLEEP_OPTIONS = "$PREFIX.SET_SLEEP_OPTIONS"
    const val ACTION_SET_OWN_SETTINGS = "$PREFIX.SET_OWN_SETTINGS"

    /** Starts reading [ARG_BOOK_ID] at [ARG_PAGE] / [ARG_CHAR_START]. Only ever send it from a user action (C7). */
    val PLAY_FROM = SessionCommand(ACTION_PLAY_FROM, Bundle.EMPTY)

    /** Switches the voice for one language; the current sentence restarts in the new voice. */
    val SET_VOICE = SessionCommand(ACTION_SET_VOICE, Bundle.EMPTY)

    /** Sets or clears the sleep timer. */
    val SET_SLEEP_TIMER = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)

    /** Sets the speaking rate and pace; the current sentence restarts with them, like a voice change. */
    val SET_SPEECH_STYLE = SessionCommand(ACTION_SET_SPEECH_STYLE, Bundle.EMPTY)

    /** "This sounded wrong" (RI-8b): the service logs it with the voice reading that language; nothing else. */
    val REPORT_SOUNDED_WRONG = SessionCommand(ACTION_REPORT_SOUNDED_WRONG, Bundle.EMPTY)

    /** Back to where reading was before the last jump ([SESSION_UNDO_PAGE]); refused when there is none. */
    val UNDO_JUMP = SessionCommand(ACTION_UNDO_JUMP, Bundle.EMPTY)

    /** Saves the sleep timer extras ([ARG_SLEEP_OPTIONS]): shake to extend, fade-out, bedtime (plan item 37). */
    val SET_SLEEP_OPTIONS = SessionCommand(ACTION_SET_SLEEP_OPTIONS, Bundle.EMPTY)

    /**
     * Gives the open book its own speed, pace and voices ([ARG_OWN_SETTINGS] true), starting from the current ones,
     * or puts it back on the global ones (plan item 4). While on, speed, pace and voice changes are the book's.
     */
    val SET_OWN_SETTINGS = SessionCommand(ACTION_SET_OWN_SETTINGS, Bundle.EMPTY)

    // Arguments of PLAY_FROM.
    const val ARG_BOOK_ID = "book_id"

    /** Zero-based page. */
    const val ARG_PAGE = "page"

    /** Offset into the page's text (a `ReadingUnit.charStart`); 0 or absent = start of the page. */
    const val ARG_CHAR_START = "char_start"

    /** For encrypted books: kept in memory for this session only, never stored or logged. */
    const val ARG_PASSWORD = "password"

    // Arguments of SET_VOICE.
    const val ARG_VOICE_ENGINE = "voice_engine"
    const val ARG_VOICE_NAME = "voice_name"

    /** `Lang.name`: which language's sentences the voice reads. */
    const val ARG_VOICE_LANG = "voice_lang"

    // Arguments of SET_SLEEP_TIMER.

    /** A [SleepTimerMode] name. */
    const val ARG_SLEEP_MODE = "sleep_mode"
    const val ARG_SLEEP_MINUTES = "sleep_minutes"

    /** Int: chapter ends an end-of-chapter timer waits for; absent = 1. */
    const val ARG_SLEEP_CHAPTERS = "sleep_chapters"

    /** String: [SleepOptions.encode], the argument of SET_SLEEP_OPTIONS. */
    const val ARG_SLEEP_OPTIONS = "sleep_options"

    /** Boolean, the argument of SET_OWN_SETTINGS. */
    const val ARG_OWN_SETTINGS = "own_settings"

    // Arguments of SET_SPEECH_STYLE.

    /** Float, 1.0 = the voice's own speed; clamped to the offered range. */
    const val ARG_SPEECH_RATE = "speech_rate"

    /** A [Pace] name. */
    const val ARG_PACE = "pace"

    /** Boolean: pauses at dashes, colons, brackets and the like inside a sentence (RI-6); absent = on. */
    const val ARG_CLAUSE_PAUSES = "clause_pauses"

    /** String: [ReadingModes.encode]; absent or unreadable = the defaults (ADR-035). */
    const val ARG_READING_MODES = "reading_modes"

    /** String: [VoiceShaping.encode]; absent or unreadable = the defaults (plan items 9, 10). */
    const val ARG_VOICE_SHAPING = "voice_shaping"

    // Arguments of REPORT_SOUNDED_WRONG (and ARG_BOOK_ID, ARG_PAGE): where the sentence is and how it was judged.
    const val ARG_UNIT_INDEX = "unit_index"
    const val ARG_UNIT_KIND = "unit_kind"

    /** Float; absent = the sentence was not scored. */
    const val ARG_SPEAK_SCORE = "speak_score"
    const val ARG_UNIT_LANG = "unit_lang"
    const val ARG_REPORT_REASON = "report_reason"

    // Error result of a custom command: SessionResult.extras[RESULT_ERROR] is a [PlaybackErrorCode] name.
    const val RESULT_ERROR = "error"

    // MediaMetadata.extras of every sentence item.
    const val ITEM_BOOK_ID = "book_id"
    const val ITEM_PAGE = "page"
    const val ITEM_PAGE_COUNT = "page_count"

    /** Sentence index within its page: display only, never a resume key (Build Spec §4). */
    const val ITEM_UNIT_INDEX = "unit_index"
    const val ITEM_CHAR_START = "char_start"
    const val ITEM_CHAR_END = "char_end"

    // Session extras, updated as playback moves.

    /** The book being read; absent when nothing is loaded. */
    const val SESSION_BOOK_ID = "book_id"

    /** The [SleepTimerMode] name in effect; absent = off. */
    const val SESSION_SLEEP_MODE = "sleep_mode"

    /** Wall-clock millis when a [SleepTimerMode.MINUTES] timer pauses playback. */
    const val SESSION_SLEEP_ENDS_AT = "sleep_ends_at_millis"

    /** Int: chapter ends an end-of-chapter timer still waits for, when more than one. */
    const val SESSION_SLEEP_CHAPTERS = "sleep_chapters"

    /** The sleep timer extras in effect ([SleepOptions.encode]), always present. */
    const val SESSION_SLEEP_OPTIONS = "sleep_options"

    /** True while the open book has its own speed, pace and voices; absent = false. */
    const val SESSION_OWN_SETTINGS = "own_settings"

    /** The open book's own voices (`BookSettings.encodeVoices`), while it has its own settings. */
    const val SESSION_BOOK_VOICES = "book_voices"

    /** True when the open book has chapters, so [SleepTimerMode.END_OF_CHAPTER] can be set; absent = false. */
    const val SESSION_HAS_CHAPTERS = "has_chapters"

    /** The last [PlaybackErrorCode] name that stopped or skipped reading; absent when none. */
    const val SESSION_ERROR = "error"

    /** Human-readable detail for [SESSION_ERROR], safe to show (never the PDF's text). */
    const val SESSION_ERROR_MESSAGE = "error_message"

    /** The speaking rate in effect (Float), always present. */
    const val SESSION_SPEECH_RATE = "speech_rate"

    /** The [Pace] name in effect, always present. */
    const val SESSION_PACE = "pace"

    /** Whether clause pauses are on (Boolean), always present. */
    const val SESSION_CLAUSE_PAUSES = "clause_pauses"

    /** The reading modes in effect ([ReadingModes.encode]), always present. */
    const val SESSION_READING_MODES = "reading_modes"

    /** The voice shaping in effect ([VoiceShaping.encode]), always present. */
    const val SESSION_VOICE_SHAPING = "voice_shaping"

    /** Int pair: where "Undo jump" goes back to in the book being read (plan item 78); absent with nothing to undo. */
    const val SESSION_UNDO_PAGE = "undo_page"
    const val SESSION_UNDO_CHAR = "undo_char"

    /** Token for `MediaController.Builder(context, PlaybackContract.sessionToken(context))`. */
    fun sessionToken(context: Context): SessionToken =
        SessionToken(context, ComponentName(context, AudoraPlaybackService::class.java))

    fun playFromArgs(bookId: String, page: Int, charStart: Int = 0, password: String? = null): Bundle = Bundle().apply {
        putString(ARG_BOOK_ID, bookId)
        putInt(ARG_PAGE, page)
        putInt(ARG_CHAR_START, charStart)
        if (password != null) putString(ARG_PASSWORD, password)
    }

    fun setVoiceArgs(voice: VoiceId, lang: Lang): Bundle = Bundle().apply {
        putString(ARG_VOICE_ENGINE, voice.engine.name)
        putString(ARG_VOICE_NAME, voice.name)
        putString(ARG_VOICE_LANG, lang.name)
    }

    fun sleepTimerArgs(mode: SleepTimerMode, minutes: Int = 0, chapters: Int = 1): Bundle = Bundle().apply {
        putString(ARG_SLEEP_MODE, mode.name)
        putInt(ARG_SLEEP_MINUTES, minutes)
        putInt(ARG_SLEEP_CHAPTERS, chapters)
    }

    fun sleepOptionsArgs(options: SleepOptions): Bundle =
        Bundle().apply { putString(ARG_SLEEP_OPTIONS, options.encode()) }

    fun speechStyleArgs(style: SpeechStyle): Bundle = Bundle().apply {
        putFloat(ARG_SPEECH_RATE, style.rate)
        putString(ARG_PACE, style.pace.name)
        putBoolean(ARG_CLAUSE_PAUSES, style.clausePauses)
        putString(ARG_READING_MODES, style.modes.encode())
        putString(ARG_VOICE_SHAPING, style.shaping.encode())
    }

    /** Parses [speechStyleArgs]; null when a field is missing, unknown or not a positive number. */
    fun speechStyleFrom(args: Bundle): SpeechStyle? {
        val rate = args.getFloat(ARG_SPEECH_RATE, Float.NaN).takeIf { it > 0f && it.isFinite() }
        val pace = enumOrNull<Pace>(args.getString(ARG_PACE))
        val clausePauses = args.getBoolean(ARG_CLAUSE_PAUSES, true)
        val modes = ReadingModes.decode(args.getString(ARG_READING_MODES))
        val shaping = VoiceShaping.decode(args.getString(ARG_VOICE_SHAPING))
        return if (rate == null || pace == null) null else SpeechStyle(rate, pace, clausePauses, modes, shaping)
    }

    /** Parses [setVoiceArgs]; null when a field is missing or unknown. */
    fun voiceFrom(args: Bundle): Pair<VoiceId, Lang>? {
        val engine = enumOrNull<EngineId>(args.getString(ARG_VOICE_ENGINE))
        val lang = enumOrNull<Lang>(args.getString(ARG_VOICE_LANG))
        val name = args.getString(ARG_VOICE_NAME)?.takeIf { it.isNotBlank() }
        return if (engine == null || lang == null || name == null) null else VoiceId(engine, name) to lang
    }

    fun reportArgs(report: SoundedWrongReport): Bundle = Bundle().apply {
        putString(ARG_BOOK_ID, report.bookId.value)
        putInt(ARG_PAGE, report.page)
        putInt(ARG_UNIT_INDEX, report.unitIndex)
        putString(ARG_UNIT_KIND, report.kind.name)
        report.speakScore?.let { putFloat(ARG_SPEAK_SCORE, it) }
        putString(ARG_UNIT_LANG, report.lang.name)
        putString(ARG_REPORT_REASON, report.reason.name)
    }

    /** Parses [reportArgs]; null when a field is missing or unknown. */
    @Suppress("ComplexCondition") // Every field but the score is required.
    fun reportFrom(args: Bundle): SoundedWrongReport? {
        val bookId = args.getString(ARG_BOOK_ID)?.takeIf { it.isNotBlank() }?.let(::BookId)
        val page = args.getInt(ARG_PAGE, -1).takeIf { it >= 0 }
        val index = args.getInt(ARG_UNIT_INDEX, -1).takeIf { it >= 0 }
        val kind = enumOrNull<UnitKind>(args.getString(ARG_UNIT_KIND))
        val lang = enumOrNull<Lang>(args.getString(ARG_UNIT_LANG))
        val reason = enumOrNull<SoundedWrongReason>(args.getString(ARG_REPORT_REASON))
        val score = if (args.containsKey(ARG_SPEAK_SCORE)) args.getFloat(ARG_SPEAK_SCORE) else null
        return if (bookId == null || page == null || index == null || kind == null || lang == null || reason == null) {
            null
        } else {
            SoundedWrongReport(bookId, page, index, kind, score, lang, reason)
        }
    }

    fun sleepModeFrom(args: Bundle): SleepTimerMode? = enumOrNull<SleepTimerMode>(args.getString(ARG_SLEEP_MODE))

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
