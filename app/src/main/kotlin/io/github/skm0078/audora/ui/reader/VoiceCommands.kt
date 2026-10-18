// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommands.kt
// Purpose: "Audora, pause" and the other spoken commands, heard while the reader is open (plan item 38).

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.commands.VoiceCommandService
import io.github.skm0078.audora.speech.kws.CommandListener
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Listens per [mode] while this reader is open (the switch is on and the model installed). Commands reach [onCommand]
 * on the main thread, in the order heard.
 * - [VoiceCommandsMode.WHILE_VISIBLE]: from onStart to onStop, so the microphone is never open with the app in the
 *   background. Paused counts too, so "Audora, play" works.
 * - [VoiceCommandsMode.SCREEN_OFF]: a session starts when the reader is shown, with [VoiceCommandService] keeping the
 *   microphone usable in the background (Android 14 starts it only while the app is visible), and goes on with the
 *   screen off. It ends when Stop is tapped, when the reader closes, or once reading has been paused in the
 *   background for `screenOffIdleMs`; the next one starts when the reader is shown again.
 */
@Composable
fun VoiceCommandsEffect(
    mode: VoiceCommandsMode,
    listener: CommandListener,
    reading: Flow<Boolean>,
    config: VoiceCommandConfig = VoiceCommandConfig(),
    onCommand: (VoiceCommand) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle by rememberUpdatedState(onCommand)
    LaunchedEffect(mode, listener, lifecycle) {
        val heard: (VoiceCommand) -> Unit = { handle(it) }
        when (mode) {
            VoiceCommandsMode.OFF -> Unit

            VoiceCommandsMode.WHILE_VISIBLE -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                listen(listener, heard)
            }

            VoiceCommandsMode.SCREEN_OFF -> {
                val visible = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
                val active = combine(visible, reading) { shown, playing -> shown || playing }
                while (true) {
                    visible.first { it }
                    screenOffSession(context, listener, active, config, heard)
                    // The next session starts only when the reader is shown again.
                    visible.first { !it }
                }
            }
        }
    }
}

/** Hears commands until cancelled or the listener stops (logged). */
private suspend fun listen(listener: CommandListener, onCommand: (VoiceCommand) -> Unit) = coroutineScope {
    val heard = Channel<VoiceCommand>(Channel.UNLIMITED)
    launch {
        val outcome = listener.listen { heard.trySend(it) }
        if (outcome is Outcome.Failure) AudoraLog.w(TAG, "Voice commands not listening: ${outcome.error}")
        heard.close()
    }
    for (command in heard) onCommand(command)
}

/** One screen-off listening session; [active] is "the reader is shown, or reading goes on". */
private suspend fun screenOffSession(
    context: Context,
    listener: CommandListener,
    active: Flow<Boolean>,
    config: VoiceCommandConfig,
    onCommand: (VoiceCommand) -> Unit,
) {
    val endingsBefore = VoiceCommandService.endings.value
    VoiceCommandService.start(context)
    try {
        coroutineScope {
            val done = CompletableDeferred<String>()
            val waits = listOf(
                launch {
                    listen(listener, onCommand)
                    done.complete("the listener stopped")
                },
                launch {
                    VoiceCommandService.endings.first { it > endingsBefore }
                    done.complete("Stop was tapped, or Android refused the service")
                },
                launch {
                    awaitIdle(active, config.screenOffIdleMs)
                    done.complete("reading stayed paused in the background")
                },
            )
            AudoraLog.i(TAG, "Screen-off listening ended: ${done.await()}")
            waits.forEach { it.cancel() }
        }
    } finally {
        VoiceCommandService.stop(context)
    }
}

/** Returns once [active] has stayed false for [idleMs]. */
private suspend fun awaitIdle(active: Flow<Boolean>, idleMs: Long) {
    var idle = false
    while (!idle) {
        active.first { !it }
        idle = withTimeoutOrNull(idleMs) { active.first { it } } == null
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
