// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackCodes.kt
// Purpose: The contract's enums (sleep-timer modes, error codes), kept free of Android types so pure code uses them.

package io.github.skm0078.audora.playback.contract

enum class SleepTimerMode {
    OFF,

    /** Pause after [PlaybackContract.ARG_SLEEP_MINUTES] minutes. */
    MINUTES,

    /** Pause when the last sentence of the current page has been read. */
    END_OF_PAGE,

    /** Pause before the page where the next chapter starts. Only when [PlaybackContract.SESSION_HAS_CHAPTERS]. */
    END_OF_CHAPTER,
}

/** Why a command failed or reading stopped. Names are stable: the UI maps them to its own strings. */
enum class PlaybackErrorCode {
    /** A custom command had a missing or invalid argument. */
    BAD_REQUEST,

    /** The book id is not in the library. */
    BOOK_NOT_FOUND,

    /** The book is encrypted; send PLAY_FROM again with [PlaybackContract.ARG_PASSWORD]. */
    PASSWORD_REQUIRED,
    INCORRECT_PASSWORD,

    /** The PDF could not be opened or read (moved, deleted, damaged). */
    BOOK_UNREADABLE,

    /** The voice cannot speak (not installed, model files missing, engine failed repeatedly). */
    VOICE_UNAVAILABLE,

    /** Synthesis or the player kept failing and recovery gave up. */
    PLAYBACK_FAILED,
}
