// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommands.kt
// Purpose: "Audora, pause" and the other spoken commands, heard while the reader is on screen (plan item 38).

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.kws.CommandListener
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Listens while [active] (the switch is on and the model installed) and the reader is visible: from onStart to
 * onStop, so the microphone is never open with the app in the background, which Android would only allow from a
 * microphone foreground service. Paused counts too, so "Audora, play" works. Commands reach [onCommand] on the main
 * thread, in the order heard.
 */
@Composable
fun VoiceCommandsEffect(active: Boolean, listener: CommandListener, onCommand: (VoiceCommand) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle by rememberUpdatedState(onCommand)
    LaunchedEffect(active, listener, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val heard = Channel<VoiceCommand>(Channel.UNLIMITED)
            launch {
                val outcome = listener.listen { heard.trySend(it) }
                if (outcome is Outcome.Failure) AudoraLog.w(TAG, "Voice commands not listening: ${outcome.error}")
                heard.close()
            }
            for (command in heard) handle(command)
        }
    }
}

/**
 * What each command does in the reader: the same as its button. Play and pause act only when they change something,
 * so "Audora, pause" said twice never starts reading again.
 */
fun ReaderModel.onVoiceCommand(command: VoiceCommand, config: ReaderConfig) {
    val reading = state.value.isReading
    when (command) {
        VoiceCommand.PAUSE -> if (reading) playOrPause()

        VoiceCommand.PLAY -> if (!reading) playOrPause()

        VoiceCommand.NEXT -> skipForward()

        VoiceCommand.BACK -> skipBack()

        VoiceCommand.REPEAT -> replayPrevious()

        VoiceCommand.FASTER, VoiceCommand.SLOWER ->
            config.speedStep(state.value.playback.speed, command == VoiceCommand.FASTER)?.let(::setSpeed)
    }
}

private const val TAG = "Audora.VoiceCommands"
