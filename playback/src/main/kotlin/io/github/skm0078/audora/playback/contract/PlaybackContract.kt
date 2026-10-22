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
import io.github.skm0078.audora.core.model.FocusSoundOptions
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
import io.github.skm0078.audora.playback.session.Spot
import io.github.skm0078.audora.playback.session.StudyMarks
import io.github.skm0078.audora.playback.session.StudyRange
import io.github.skm0078.audora.playback.session.isAfter

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
    const val ACTION_REPLAY_PREVIOUS = "$PREFIX.REPLAY_PREVIOUS"
    const val ACTION_STUDY = "$PREFIX.STUDY"
    const val ACTION_SET_FOCUS_SOUND = "$PREFIX.SET_FOCUS_SOUND"
    const val ACTION_SKIP_PARAGRAPH = "$PREFIX.SKIP_PARAGRAPH"

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

    /** Saves the focus sound under the voice ([ARG_FOCUS_SOUND]): which one and how loud (plan item 72). */
    val SET_FOCUS_SOUND = SessionCommand(ACTION_SET_FOCUS_SOUND, Bundle.EMPTY)

    /**
     * To the next paragraph ([ARG_FORWARD] true) or back to this paragraph's start, the one before when already at it
     * (long-press on next / previous). Fails with BAD_REQUEST, like REPLAY_PREVIOUS, when there is none that way.
     */
    val SKIP_PARAGRAPH = SessionCommand(ACTION_SKIP_PARAGRAPH, Bundle.EMPTY)

    /**
     * Gives the open book its own speed, pace and voices ([ARG_OWN_SETTINGS] true), starting from the current ones,
     * or puts it back on the global ones (plan item 4). While on, speed, pace and voice changes are the book's.
     */
    val SET_OWN_SETTINGS = SessionCommand(ACTION_SET_OWN_SETTINGS, Bundle.EMPTY)

    /**
     * "What did it say?": replays the sentence before the current one at [ARG_REPLAY_FACTOR] times the listener's
     * speed, then reading goes on at their speed. One shot: nothing is saved, unlike SET_SPEECH_STYLE. Refused when no
     * sentence was played before the current one.
     */
    val REPLAY_PREVIOUS = SessionCommand(ACTION_REPLAY_PREVIOUS, Bundle.EMPTY)

    /**
     * Study loops (plan item 31): [ARG_STUDY_ACTION] marks A or B at the sentence playing, turns looping on or off
     * ([ARG_STUDY_LOOP]), clears the range, or plays an exact range of a book ([StudyAction.RANGE]: PLAY_FROM's
     * arguments for its start plus [ARG_END_PAGE] / [ARG_END_CHAR], the last character, inclusive). The range is
     * published in the `SESSION_STUDY_*` extras. Repeats and pauses after each sentence are reading modes instead.
     */
    val STUDY = SessionCommand(ACTION_STUDY, Bundle.EMPTY)

    // Arguments of PLAY_FROM.
    const val ARG_BOOK_ID = "book_id"

    /** Zero-based page. */
    const val ARG_PAGE = "page"

    /** Offset into the page's text (a `ReadingUnit.charStart`); 0 or absent = start of the page. */
    const val ARG_CHAR_START = "char_start"

    // ARG_UNIT_INDEX (below, shared with REPORT_SOUNDED_WRONG): the start unit's index on ARG_PAGE when the start
    // is a known unit (a tap, a rebuild); absent for a saved resume, which then starts at the first unit there.

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

    /** String: [FocusSoundOptions.encode], the argument of SET_FOCUS_SOUND. */
    const val ARG_FOCUS_SOUND = "focus_sound"

    /** Boolean, the argument of SKIP_PARAGRAPH: true = next paragraph, false = back. */
    const val ARG_FORWARD = "forward"

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

    // Arguments of STUDY (and PLAY_FROM's for a range's start).

    /** A [StudyAction] name. */
    const val ARG_STUDY_ACTION = "study_action"

    /** Boolean: the range loops; absent = true. */
    const val ARG_STUDY_LOOP = "study_loop"
    const val ARG_END_PAGE = "end_page"
    const val ARG_END_CHAR = "end_char"

    /** Float in [REPLAY_FACTOR_RANGE], the argument of REPLAY_PREVIOUS: relative to the speed the listener hears. */
    const val ARG_REPLAY_FACTOR = "replay_factor"

    /** Half speed at the slowest (slower smears the words); 1 = the listener's own speed. */
    val REPLAY_FACTOR_RANGE = MIN_REPLAY_FACTOR..1f
    private const val MIN_REPLAY_FACTOR = 0.5f

    // Arguments of REPORT_SOUNDED_WRONG (and ARG_BOOK_ID, ARG_PAGE): where the sentence is and how it was judged.

    /** A unit's index on ARG_PAGE; also PLAY_FROM's optional start unit. */
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

    /** The focus sound in effect ([FocusSoundOptions.encode]), always present. */
    const val SESSION_FOCUS_SOUND = "focus_sound"

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

    /**
     * String: [SentenceTiming.encode] for the sentence playing, once it is rendered; absent before (plan item 14).
     * Match its page and char start against the current item's before using it.
     */
    const val SESSION_SENTENCE_TIMING = "sentence_timing"

    /** Int pair: the study range's A (plan item 31); absent with no A. */
    const val SESSION_STUDY_START_PAGE = "study_start_page"
    const val SESSION_STUDY_START_CHAR = "study_start_char"

    /** Int pair: the study range's B; absent until B is set. */
    const val SESSION_STUDY_END_PAGE = "study_end_page"
    const val SESSION_STUDY_END_CHAR = "study_end_char"

    /** Boolean: whether the range loops; present with A. */
    const val SESSION_STUDY_LOOP = "study_loop"

    /**
     * The narrator blend (plan item 75): the narrator's own Kokoro English speaker, present only while such a voice
     * narrates (the only one a blend changes); and the bytes a blend that could not be built needed free.
     */
    const val SESSION_BLEND_OWN_SPEAKER = "blend_own_speaker"
    const val SESSION_BLEND_NEEDS_BYTES = "blend_needs_bytes"

    /**
     * Boolean: whether a voice reading now honours expressiveness (plan item 8: Piper and MMS only); absent while no
     * voice is known yet.
     */
    const val SESSION_VOICE_EXPRESSIVE = "voice_expressive"

    /** Int pair: where "Undo jump" goes back to in the book being read (plan item 78); absent with nothing to undo. */
    const val SESSION_UNDO_PAGE = "undo_page"
    const val SESSION_UNDO_CHAR = "undo_char"

    /** Token for `MediaController.Builder(context, PlaybackContract.sessionToken(context))`. */
    fun sessionToken(context: Context): SessionToken =
        SessionToken(context, ComponentName(context, AudoraPlaybackService::class.java))

    fun playFromArgs(
        bookId: String,
        page: Int,
        charStart: Int = 0,
        password: String? = null,
        unitIndex: Int? = null,
    ): Bundle = Bundle().apply {
        putString(ARG_BOOK_ID, bookId)
        putInt(ARG_PAGE, page)
        putInt(ARG_CHAR_START, charStart)
        if (password != null) putString(ARG_PASSWORD, password)
        if (unitIndex != null) putInt(ARG_UNIT_INDEX, unitIndex)
    }

    /** The unit index [playFromArgs] or [reportArgs] carry; null when absent (an offset-only start) or negative. */
    fun unitIndexFrom(args: Bundle): Int? = args.getInt(ARG_UNIT_INDEX, -1).takeIf { it >= 0 }

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

    fun studyArgs(action: StudyAction, loop: Boolean = true): Bundle = Bundle().apply {
        putString(ARG_STUDY_ACTION, action.name)
        putBoolean(ARG_STUDY_LOOP, loop)
    }

    /** A [StudyAction.RANGE] request: [bookId] from [start] through [end] (inclusive). */
    fun studyRangeArgs(bookId: String, start: Spot, end: Spot, loop: Boolean, password: String? = null): Bundle =
        playFromArgs(bookId, start.page, start.charStart, password).apply {
            putAll(studyArgs(StudyAction.RANGE, loop))
            putInt(ARG_END_PAGE, end.page)
            putInt(ARG_END_CHAR, end.charStart)
        }

    /** Parses [studyArgs]; null when the action is missing or unknown. */
    fun studyActionFrom(args: Bundle): StudyAction? = enumOrNull<StudyAction>(args.getString(ARG_STUDY_ACTION))

    /** The range of a [StudyAction.RANGE] request; null when a position is missing, negative or backwards. */
    fun studyRangeFrom(args: Bundle): StudyRange? {
        val start = Spot(args.getInt(ARG_PAGE, -1), args.getInt(ARG_CHAR_START, -1))
        val end = Spot(args.getInt(ARG_END_PAGE, -1), args.getInt(ARG_END_CHAR, -1))
        val placed = listOf(start.page, start.charStart, end.page, end.charStart).all { it >= 0 }
        val valid = placed && !start.isAfter(end)
        return if (valid) StudyRange(start, end, args.getBoolean(ARG_STUDY_LOOP, true)) else null
    }

    fun studyExtras(marks: StudyMarks): Bundle = Bundle().apply {
        putInt(SESSION_STUDY_START_PAGE, marks.start.page)
        putInt(SESSION_STUDY_START_CHAR, marks.start.charStart)
        marks.end?.let {
            putInt(SESSION_STUDY_END_PAGE, it.page)
            putInt(SESSION_STUDY_END_CHAR, it.charStart)
        }
        putBoolean(SESSION_STUDY_LOOP, marks.loop)
    }

    /** Reads [studyExtras] back; null with no A. */
    fun studyMarksFrom(extras: Bundle): StudyMarks? {
        if (!extras.containsKey(SESSION_STUDY_START_PAGE)) return null
        val start = Spot(extras.getInt(SESSION_STUDY_START_PAGE), extras.getInt(SESSION_STUDY_START_CHAR))
        val end = if (extras.containsKey(SESSION_STUDY_END_PAGE)) {
            Spot(extras.getInt(SESSION_STUDY_END_PAGE), extras.getInt(SESSION_STUDY_END_CHAR))
        } else {
            null
        }
        return StudyMarks(start, end, extras.getBoolean(SESSION_STUDY_LOOP, true))
    }

    fun replayPreviousArgs(factor: Float): Bundle = Bundle().apply { putFloat(ARG_REPLAY_FACTOR, factor) }

    /** Parses [replayPreviousArgs]; null when absent or outside [REPLAY_FACTOR_RANGE]. */
    fun replayFactorFrom(args: Bundle): Float? =
        args.getFloat(ARG_REPLAY_FACTOR, Float.NaN).takeIf { it in REPLAY_FACTOR_RANGE }

    fun sleepOptionsArgs(options: SleepOptions): Bundle =
        Bundle().apply { putString(ARG_SLEEP_OPTIONS, options.encode()) }

    fun skipParagraphArgs(forward: Boolean): Bundle = Bundle().apply { putBoolean(ARG_FORWARD, forward) }

    fun focusSoundArgs(options: FocusSoundOptions): Bundle =
        Bundle().apply { putString(ARG_FOCUS_SOUND, options.encode()) }

    /**
     * Only the fields given are sent; the service puts them on top of the style it has saved
     * ([SpeechStyleChange.over]), so a controller that doesn't know the saved style yet can never reset the rest, and
     * two quick changes to different fields can't undo each other.
     */
    fun speechStyleArgs(change: SpeechStyleChange): Bundle = Bundle().apply {
        change.rate?.let { putFloat(ARG_SPEECH_RATE, it) }
        change.pace?.let { putString(ARG_PACE, it.name) }
        change.clausePauses?.let { putBoolean(ARG_CLAUSE_PAUSES, it) }
        change.modes?.let { putString(ARG_READING_MODES, it.encode()) }
        change.shaping?.let { putString(ARG_VOICE_SHAPING, it.encode()) }
    }

    /**
     * Parses [speechStyleArgs]; null when no field was sent or one is unreadable (a rate that is not a positive
     * number, an unknown pace), so a bad request changes nothing. Unreadable modes or shaping take their defaults.
     */
    fun speechStyleChangeFrom(args: Bundle): SpeechStyleChange? {
        val rate = if (args.containsKey(ARG_SPEECH_RATE)) args.getFloat(ARG_SPEECH_RATE, Float.NaN) else null
        val paceName = args.getString(ARG_PACE)
        val pace = paceName?.let { enumOrNull<Pace>(it) }
        val unreadable = (rate != null && !(rate > 0f && rate.isFinite())) || (paceName != null && pace == null)
        return if (unreadable || STYLE_KEYS.none(args::containsKey)) {
            null
        } else {
            SpeechStyleChange(
                rate = rate,
                pace = pace,
                clausePauses = if (args.containsKey(ARG_CLAUSE_PAUSES)) args.getBoolean(ARG_CLAUSE_PAUSES) else null,
                modes = args.getString(ARG_READING_MODES)?.let(ReadingModes::decode),
                shaping = args.getString(ARG_VOICE_SHAPING)?.let(VoiceShaping::decode),
            )
        }
    }

    private val STYLE_KEYS = listOf(ARG_SPEECH_RATE, ARG_PACE, ARG_CLAUSE_PAUSES, ARG_READING_MODES, ARG_VOICE_SHAPING)

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
        val index = unitIndexFrom(args)
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
