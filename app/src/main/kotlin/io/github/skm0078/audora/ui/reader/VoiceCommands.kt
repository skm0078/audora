// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommands.kt
// Purpose: "ListenLock, pause" and the other spoken commands, heard while the reader is open (plan item 38).

package io.github.skm0078.audora.ui.reader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.commands.ListeningNotice
import io.github.skm0078.audora.playback.commands.VoiceCommandService
import io.github.skm0078.audora.speech.kws.CommandListener
import io.github.skm0078.audora.speech.kws.VoiceCommand
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Listens per [mode] (already [VoiceCommandsMode.effective]) while this reader is open. Commands reach [onCommand] on
 * the main thread, in the order heard, each confirmed by a short tone.
 * - [VoiceCommandsMode.WHILE_VISIBLE]: from onStart to onStop, so the microphone is never open with the app in the
 *   background. Paused counts too, so "ListenLock, play" works.
 * - [VoiceCommandsMode.SCREEN_OFF]: [ScreenOffListening], with [VoiceCommandService]; [inBackground] is told while a
 *   session runs, so the reader keeps its player connected with the screen off.
 */
@Composable
fun VoiceCommandsEffect(
    mode: VoiceCommandsMode,
    listener: CommandListener,
    reading: Flow<Boolean>,
    inBackground: (Boolean) -> Unit,
    config: VoiceCommandConfig = VoiceCommandConfig(),
    onCommand: (VoiceCommand) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle by rememberUpdatedState(onCommand)
    val background by rememberUpdatedState(inBackground)
    val tone = remember(config) { CommandTone(config) }
    DisposableEffect(tone) { onDispose { tone.release() } }
    LaunchedEffect(mode, listener, reading, lifecycle) {
        val heard: (VoiceCommand) -> Unit = {
            tone.play()
            handle(it)
        }
        val visible = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
        val listen: Listen = { listener.listen(it) }
        when (mode) {
            VoiceCommandsMode.OFF -> Unit

            VoiceCommandsMode.WHILE_VISIBLE -> while (true) {
                visible.first { it }
                listenWhileVisible(visible, listen, heard)
            }

            VoiceCommandsMode.SCREEN_OFF -> {
                val service = AndroidListeningService(context, config)
                ScreenOffListening(service, listen, visible, reading, config.screenOffIdleMs) { background(it) }
                    .run(heard)
            }
        }
    }
}

/** Voice commands in the reader, as Settings, the model and the phone's permissions allow. */
@Composable
fun ReaderVoiceCommands(
    model: ReaderModel,
    preference: ScreenPreference,
    models: VoiceCommandModels,
    config: ReaderConfig,
    inBackground: (Boolean) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val saved by remember(preference) { preference.voiceCommands() }.collectAsStateWithLifecycle(VoiceCommandsMode.OFF)
    val mode = rememberEffectiveVoiceCommands(saved, preference)
    val installed by remember(models) { models.installed() }.collectAsStateWithLifecycle(false)
    val listener = remember(models) { CommandListener(context, models) }
    val reading = remember(model) { model.state.map { it.isReading }.distinctUntilChanged() }
    VoiceCommandsEffect(mode.takeIf { installed } ?: VoiceCommandsMode.OFF, listener, reading, inBackground) {
        model.onVoiceCommand(it, config)
    }
}

/**
 * [saved] as it can run on this phone now ([VoiceCommandsMode.effective]), re-checked each time the screen resumes
 * (a permission granted or taken back in Android's settings). A saved mode without the microphone is saved as off,
 * once, so a later grant (another feature asking for the microphone) never turns listening back on unasked.
 */
@Composable
fun rememberEffectiveVoiceCommands(saved: VoiceCommandsMode, preference: ScreenPreference): VoiceCommandsMode {
    val context = LocalContext.current.applicationContext
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val microphone = remember(resumed) { microphoneAllowed(context) }
    val notifications = remember(resumed) { notificationsAllowed(context) }
    LaunchedEffect(saved, microphone) {
        // Checked again here: a grant can land between composing and this running.
        if (saved != VoiceCommandsMode.OFF && !microphoneAllowed(context)) {
            AudoraLog.i(TAG, "Voice commands were $saved without the microphone; saved as off")
            preference.saveVoiceCommands(VoiceCommandsMode.OFF)
        }
    }
    return saved.effective(microphone, notifications)
}

fun microphoneAllowed(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Whether Audora's notifications show (the permission on Android 13+, the app's switch before). */
fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** [VoiceCommandService], for [ScreenOffListening]. */
private class AndroidListeningService(private val context: Context, config: VoiceCommandConfig) : ListeningService {
    private val idleMinutes = config.screenOffIdleMinutes

    override val endings: StateFlow<Int> get() = VoiceCommandService.endings

    override fun start() = VoiceCommandService.start(context)

    override fun end(notice: ListeningNotice) = VoiceCommandService.end(context, notice, idleMinutes)

    override fun silenced(silenced: Boolean) = VoiceCommandService.silenced(context, silenced)
}

/**
 * A short beep per command acted on, so a command heard by mistake with the screen off is noticed. Best effort: a
 * phone that refuses a tone generator acts on commands without it.
 */
private class CommandTone(private val config: VoiceCommandConfig) {
    @Suppress("TooGenericExceptionCaught") // ToneGenerator reports a failed init only as a bare RuntimeException
    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, config.toneVolume)
    } catch (e: RuntimeException) {
        AudoraLog.w(TAG, "No tone generator; commands act without a tone", e)
        null
    }

    fun play() {
        generator?.startTone(ToneGenerator.TONE_PROP_ACK, config.toneMs)
    }

    fun release() {
        generator?.release()
    }
}

/**
 * What each command does in the reader: the same as its button. Play and pause act only when they change something,
 * so "ListenLock, pause" said twice never starts reading again. A speed changed by voice is not saved: a command heard
 * by mistake changes this listen only.
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
            config.speedStep(state.value.playback.speed, command == VoiceCommand.FASTER)
                ?.let { setSpeed(it, save = false) }
    }
}

private const val TAG = "Audora.VoiceCommands"
