// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackContract.kt
// Purpose: The public contract between the playback service and any MediaController (reader screen, P1-05).

package io.github.skm0078.audora.playback.contract

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.playback.service.AudoraPlaybackService

/**
 * Everything the UI needs to drive playback, through a Media3 `MediaController` and nothing else.
 *
 * Standard player commands work as usual: play, pause, `seekToNextMediaItem` / `seekToPreviousMediaItem`
 * (skip a sentence), `setPlaybackSpeed` (Sonic, Build Spec C6), stop. Each playlist item is one sentence;
 * its `mediaMetadata.extras` carry where it is in the book (see the `ITEM_*` keys).
 *
 * What the player API cannot say goes through custom commands ([playFrom], [setVoice], [setSleepTimer]) and
 * the session extras (`controller.sessionExtras`, `Listener.onExtrasChanged`), keys `SESSION_*`.
 */
object PlaybackContract {
    private const val PREFIX = "io.github.skm0078.audora.playback"

    const val ACTION_PLAY_FROM = "$PREFIX.PLAY_FROM"
    const val ACTION_SET_VOICE = "$PREFIX.SET_VOICE"
    const val ACTION_SET_SLEEP_TIMER = "$PREFIX.SET_SLEEP_TIMER"

    /** Starts reading [ARG_BOOK_ID] at [ARG_PAGE] / [ARG_CHAR_START]. Only ever send it from a user action (C7). */
    val PLAY_FROM = SessionCommand(ACTION_PLAY_FROM, Bundle.EMPTY)

    /** Switches the voice for one language; the current sentence restarts in the new voice. */
    val SET_VOICE = SessionCommand(ACTION_SET_VOICE, Bundle.EMPTY)

    /** Sets or clears the sleep timer. */
    val SET_SLEEP_TIMER = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)

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

    /** True when the open book has chapters, so [SleepTimerMode.END_OF_CHAPTER] can be set; absent = false. */
    const val SESSION_HAS_CHAPTERS = "has_chapters"

    /** The last [PlaybackErrorCode] name that stopped or skipped reading; absent when none. */
    const val SESSION_ERROR = "error"

    /** Human-readable detail for [SESSION_ERROR], safe to show (never the PDF's text). */
    const val SESSION_ERROR_MESSAGE = "error_message"

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

    fun sleepTimerArgs(mode: SleepTimerMode, minutes: Int = 0): Bundle = Bundle().apply {
        putString(ARG_SLEEP_MODE, mode.name)
        putInt(ARG_SLEEP_MINUTES, minutes)
    }

    /** Parses [setVoiceArgs]; null when a field is missing or unknown. */
    fun voiceFrom(args: Bundle): Pair<VoiceId, Lang>? {
        val engine = enumOrNull<EngineId>(args.getString(ARG_VOICE_ENGINE))
        val lang = enumOrNull<Lang>(args.getString(ARG_VOICE_LANG))
        val name = args.getString(ARG_VOICE_NAME)?.takeIf { it.isNotBlank() }
        return if (engine == null || lang == null || name == null) null else VoiceId(engine, name) to lang
    }

    fun sleepModeFrom(args: Bundle): SleepTimerMode? = enumOrNull<SleepTimerMode>(args.getString(ARG_SLEEP_MODE))

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
