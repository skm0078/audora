// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/VoiceCommandsSettings.kt
// Purpose: Settings rows for spoken commands (plan item 38): the model download, the switch, the screen-off opt-in.

package io.github.skm0078.audora.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.reader.ScreenPreference
import io.github.skm0078.audora.ui.reader.VoiceCommandsMode
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
 * keeps a microphone notification up while the reader is open, and costs battery for as long as it listens.
 */
@Composable
internal fun VoiceCommandsItems(settings: VoiceCommandsSettings) {
    ModelPackRow(settings.model, settings.onModel, ModelPackTexts.VOICE_COMMANDS)
    val ready = settings.model is OcrModelsState.Ready
    // Shown as off without the model, whatever was saved: nothing listens then.
    val on = settings.mode != VoiceCommandsMode.OFF && ready
    SwitchRow(
        title = R.string.commands_switch_title,
        body = if (ready) R.string.commands_switch_body else R.string.commands_switch_needs_model,
        checked = on,
        enabled = ready,
    ) { turnOn -> settings.onChange(if (turnOn) VoiceCommandsMode.WHILE_VISIBLE else VoiceCommandsMode.OFF) }
    if (on) {
        SwitchRow(
            title = R.string.commands_screen_off_title,
            body = R.string.commands_screen_off_body,
            checked = settings.mode == VoiceCommandsMode.SCREEN_OFF,
            enabled = true,
        ) { screenOff ->
            settings.onChange(if (screenOff) VoiceCommandsMode.SCREEN_OFF else VoiceCommandsMode.WHILE_VISIBLE)
        }
    }
    HorizontalDivider()
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes body: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
    )
}

/**
 * Settings' state for voice commands: [models]' state, the saved mode, and turning listening on only once the
 * microphone is allowed (asked then, never before). A refusal leaves it off and says why.
 */
@Composable
fun rememberVoiceCommandsSettings(models: VoiceCommandModels, preference: ScreenPreference): VoiceCommandsSettings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(models) { models.state() }.collectAsStateWithLifecycle(initialValue = null)
    val mode by remember(preference) { preference.voiceCommands() }
        .collectAsStateWithLifecycle(initialValue = VoiceCommandsMode.OFF)
    var asked by remember { mutableStateOf(VoiceCommandsMode.WHILE_VISIBLE) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Microphone permission for voice commands granted=$granted")
        if (granted) {
            scope.launch { preference.saveVoiceCommands(asked) }
        } else {
            Toast.makeText(context, R.string.commands_permission_denied, Toast.LENGTH_LONG).show()
        }
    }
    return VoiceCommandsSettings(
        model = state,
        mode = mode,
        onModel = { action -> scope.runModelAction(action, models::download, models::cancel) { models.delete() } },
        onChange = { next ->
            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (next != VoiceCommandsMode.OFF && !allowed) {
                asked = next
                ask.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                scope.launch { preference.saveVoiceCommands(next) }
            }
        },
    )
}

private const val TAG = "UI.VoiceCommands"
