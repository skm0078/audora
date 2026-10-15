// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/ui/SpikeScreen.kt
// Purpose: Compose layout of the spike screen. Display only; the activity owns every action.

package io.github.skm0078.audora.spike.playback.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.spike.playback.config.SpikePreset
import kotlinx.coroutines.delay

private const val REFRESH_MS = 1_000L

@Composable
fun SpikeScreen(state: SpikeUiState, actions: SpikeActions) {
    // Poll only while the screen is visible, so the test itself is not keeping the CPU busy.
    LaunchedEffect(state.visible) {
        while (state.visible) {
            actions.refresh()
            delay(REFRESH_MS)
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Audora S3 playback spike", style = MaterialTheme.typography.headlineSmall)
            state.interruption?.let { WarningCard(it) }
            RunPicker(state, actions)
            RunButtons(state, actions)
            BatteryCard(state, actions)
            ReportCard(state, actions)
        }
    }
}

@Composable
private fun RunPicker(state: SpikeUiState, actions: SpikeActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Run", style = MaterialTheme.typography.titleMedium)
            SpikePreset.entries.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = preset == state.selectedPreset,
                            enabled = !state.runActive,
                            onClick = { actions.selectPreset(preset) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = preset == state.selectedPreset, onClick = null, enabled = !state.runActive)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(preset.label, style = MaterialTheme.typography.bodyLarge)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.ownWakeLock,
                    onCheckedChange = actions::setOwnWakeLock,
                    enabled = !state.runActive,
                )
                Text(
                    "Extra wake lock (leave off unless asked)",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RunButtons(state: SpikeUiState, actions: SpikeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = actions::startRun, enabled = state.connected && !state.runActive) { Text("Start run") }
        OutlinedButton(onClick = actions::stopRun, enabled = state.connected && state.runActive) { Text("Stop run") }
    }
    if (!state.connected) Text("Connecting to the playback service…", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BatteryCard(state: SpikeUiState, actions: SpikeActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Phone and battery", style = MaterialTheme.typography.titleMedium)
            state.batteryLines.forEach { (key, value) ->
                Text("$key: $value", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions::requestBatteryExemption, enabled = !state.batteryExempt) {
                    Text("Don't optimize")
                }
                OutlinedButton(onClick = actions::openAppSettings) { Text("App settings") }
            }
        }
    }
}

@Composable
private fun ReportCard(state: SpikeUiState, actions: SpikeActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.runActive) "Live report" else "Last report", style = MaterialTheme.typography.titleMedium)
            Text(
                state.report ?: "No run yet.",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions::shareReport) { Text("Share report") }
                OutlinedButton(onClick = actions::shareLog) { Text("Share log file") }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
