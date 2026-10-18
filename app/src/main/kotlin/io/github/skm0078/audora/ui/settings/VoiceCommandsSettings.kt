// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/VoiceCommandsSettings.kt
// Purpose: Settings rows for spoken commands (plan item 38): the model download and the listening switch.

package io.github.skm0078.audora.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * @property model the voice-command model's download state; null while it loads.
 * @property on the saved switch. Turning it on asks for the microphone first ([onChange] is told only once allowed).
 */
data class VoiceCommandsSettings(
    val model: OcrModelsState?,
    val on: Boolean,
    val onModel: (OcrModelsAction) -> Unit,
    val onChange: (Boolean) -> Unit,
)

/** The model row, then the switch, which can only turn on once the model is installed. */
@Composable
internal fun VoiceCommandsItems(settings: VoiceCommandsSettings) {
    ModelPackRow(settings.model, settings.onModel, ModelPackTexts.VOICE_COMMANDS)
    val ready = settings.model is OcrModelsState.Ready
    // Shown as off without the model, whatever was saved: nothing listens then.
    val checked = settings.on && ready
    ListItem(
        headlineContent = { Text(stringResource(R.string.commands_switch_title)) },
        supportingContent = {
            Text(stringResource(if (ready) R.string.commands_switch_body else R.string.commands_switch_needs_model))
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = ready) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = ready,
            role = Role.Switch,
            onValueChange = settings.onChange,
        ),
    )
    HorizontalDivider()
}

/**
 * Settings' state for voice commands: [models]' state, the saved switch, and turning it on only once the microphone
 * is allowed (asked then, never before). A refusal leaves it off and says why.
 */
@Composable
fun rememberVoiceCommandsSettings(models: VoiceCommandModels, preference: ScreenPreference): VoiceCommandsSettings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(models) { models.state() }.collectAsStateWithLifecycle(initialValue = null)
    val on by remember(preference) { preference.voiceCommands() }.collectAsStateWithLifecycle(initialValue = false)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Microphone permission for voice commands granted=$granted")
        if (granted) {
            scope.launch { preference.saveVoiceCommands(true) }
        } else {
            Toast.makeText(context, R.string.commands_permission_denied, Toast.LENGTH_LONG).show()
        }
    }
    return VoiceCommandsSettings(
        model = state,
        on = on,
        onModel = { action -> scope.runModelAction(action, models::download, models::cancel) { models.delete() } },
        onChange = { turnOn ->
            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (turnOn && !allowed) {
                ask.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                scope.launch { preference.saveVoiceCommands(turnOn) }
            }
        },
    )
}

private const val TAG = "UI.VoiceCommands"
