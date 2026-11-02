// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommandsStatus.kt
// Purpose: Where voice commands stand for the open book (listening, starting, or why not) and the line it shows.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.kws.ListenFailure

/**
 * Where voice commands stand while they are switched on for this reader; null in [ReaderState] while they are off.
 * [listening] is true only between the microphone really opening and closing; [failure] says why listening stopped.
 */
data class VoiceCommandsStatus(val listening: Boolean = false, val failure: ListenFailure? = null) {
    @get:StringRes
    val text: Int
        get() = when {
            failure != null -> failure.message
            listening -> R.string.reader_commands_listening
            else -> R.string.reader_commands_starting
        }

    private val ListenFailure.message: Int
        get() = when (this) {
            ListenFailure.NO_PERMISSION -> R.string.reader_commands_no_permission
            ListenFailure.NOT_AVAILABLE -> R.string.reader_commands_not_available
            ListenFailure.LANGUAGE_MISSING -> R.string.reader_commands_language_missing
            ListenFailure.MICROPHONE_BUSY -> R.string.reader_commands_busy
            ListenFailure.MICROPHONE_LOST -> R.string.reader_commands_lost
        }
}
