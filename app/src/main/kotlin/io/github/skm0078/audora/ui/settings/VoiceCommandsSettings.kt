// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/VoiceCommandsSettings.kt
// Purpose: Settings rows for spoken commands (plan item 38): the model download, the switch, the screen-off opt-in.

package io.github.skm0078.audora.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.reader.VoiceCommandsMode
import io.github.skm0078.audora.ui.reader.microphoneAllowed
import io.github.skm0078.audora.ui.reader.notificationsAllowed
import io.github.skm0078.audora.ui.reader.rememberEffectiveVoiceCommands
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.SwitchOption
import kotlinx.coroutines.launch

/**
 * @property model the voice-command model's download state; null while it loads.
 * @property mode the saved choice. Turning listening on asks for the microphone first ([onChange] is told only once
 *   allowed).
 */
data class VoiceCommandsSettings(
    val model: OcrModelsState?,
    val mode: VoiceCommandsMode,
    val onModel: (OcrModelsAction) -> Unit,
    val onChange: (VoiceCommandsMode) -> Unit,
)

/**
 * The model row, the switch (only once the model is installed), and under it, while on, the screen-off opt-in: it
 * keeps a microphone notification up while the reader is open, and costs battery for as long as it listens. Rows of
 * Advanced's first group, each with the group's divider before it.
 */
@Composable
internal fun VoiceCommandsItems(settings: VoiceCommandsSettings) {
    ModelPackRow(settings.model, settings.onModel, ModelPackTexts.VOICE_COMMANDS)
    val ready = settings.model is OcrModelsState.Ready
    // Shown as off without the model, whatever was saved: nothing listens then.
    val on = settings.mode != VoiceCommandsMode.OFF && ready
    OptionDivider()
    SwitchOption(
        stringResource(R.string.commands_switch_title),
        on,
        { turnOn -> settings.onChange(if (turnOn) VoiceCommandsMode.WHILE_VISIBLE else VoiceCommandsMode.OFF) },
        subtitle = stringResource(
            if (ready) R.string.row_line_voice_commands else R.string.commands_switch_needs_model,
        ),
        help = stringResource(R.string.commands_switch_body),
        enabled = ready,
    )
    if (on) {
        OptionDivider()
        SwitchOption(
            stringResource(R.string.commands_screen_off_title),
            settings.mode == VoiceCommandsMode.SCREEN_OFF,
            { screenOff ->
                settings.onChange(if (screenOff) VoiceCommandsMode.SCREEN_OFF else VoiceCommandsMode.WHILE_VISIBLE)
            },
            subtitle = stringResource(R.string.row_line_commands_screen_off),
            help = pluralStringResource(R.plurals.commands_screen_off_body, IDLE_MINUTES, IDLE_MINUTES),
        )
    }
}

/**
 * Settings' state for voice commands: [models]' state, the saved mode as it can run now
 * ([rememberEffectiveVoiceCommands]), and turning listening on only once the microphone is allowed (asked then,
 * never before). Screen-off listening also needs notifications on Android 13+, since its notification is where Stop
 * lives. A refusal leaves the setting as it was and says why.
 */
@Composable
fun rememberVoiceCommandsSettings(models: VoiceCommandModels, preference: ScreenPreference): VoiceCommandsSettings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(models) { models.state() }.collectAsStateWithLifecycle(initialValue = null)
    val saved by remember(preference) { preference.voiceCommands() }
        .collectAsStateWithLifecycle(initialValue = VoiceCommandsMode.OFF)
    val save: (VoiceCommandsMode) -> Unit = { mode -> scope.launch { preference.saveVoiceCommands(mode) } }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Notification permission for screen-off voice commands granted=$granted")
        if (granted) save(VoiceCommandsMode.SCREEN_OFF) else toast(context, R.string.commands_notifications_denied)
    }
    val needsNotifications = { notificationsNeeded(context) }
    var asked by rememberSaveable { mutableStateOf(VoiceCommandsMode.WHILE_VISIBLE) }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Microphone permission for voice commands granted=$granted")
        when {
            !granted -> {
                toast(context, R.string.commands_permission_denied)
                // After "Don't ask again" Android shows no prompt at all; its app page is the only way left.
                if (microphoneRefusedForGood(context)) openAppSettings(context)
            }

            asked == VoiceCommandsMode.SCREEN_OFF && needsNotifications() -> askNotifications.launch(NOTIFICATIONS)

            else -> save(asked)
        }
    }
    return VoiceCommandsSettings(
        model = state,
        mode = rememberEffectiveVoiceCommands(saved, preference),
        onModel = { action -> scope.runModelAction(action, models::download, models::cancel) { models.delete() } },
        onChange = { next ->
            when {
                next != VoiceCommandsMode.OFF && !microphoneAllowed(context) -> {
                    asked = next
                    askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                }

                next == VoiceCommandsMode.SCREEN_OFF && needsNotifications() -> askNotifications.launch(NOTIFICATIONS)

                next == VoiceCommandsMode.SCREEN_OFF && !notificationsAllowed(context) ->
                    toast(context, R.string.commands_notifications_denied)

                else -> save(next)
            }
        },
    )
}

/** Android 13+ with the permission not granted yet: it can be asked for. Before 13 there is nothing to ask. */
private fun notificationsNeeded(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun toast(context: Context, @StringRes text: Int) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/** POST_NOTIFICATIONS' value: the constant is Android 13's, the string is the same on every version. */
private const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

private const val TAG = "UI.VoiceCommands"

/** The idle limit the screen-off row names; the listener's own config. */
private val IDLE_MINUTES = VoiceCommandConfig().screenOffIdleMinutes

/** Android stopped asking for the microphone ("Don't ask again", or two refusals): no prompt will show. */
private fun microphoneRefusedForGood(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
}

/** Audora's page in the phone's settings, where a permission refused for good can still be allowed. */
private fun openAppSettings(context: Context) {
    val app = Uri.fromParts("package", context.packageName, null)
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
