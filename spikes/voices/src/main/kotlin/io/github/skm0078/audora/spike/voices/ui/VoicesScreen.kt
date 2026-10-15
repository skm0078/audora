// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/ui/VoicesScreen.kt
// Purpose: Compose layout of the S1 screen, top to bottom in the order the tester works. Display only.

package io.github.skm0078.audora.spike.voices.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.spike.voices.listen.ListeningStore

@Composable
fun VoicesScreen(state: VoicesUiState, actions: VoicesActions) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Audora S1 voice benchmark", style = MaterialTheme.typography.headlineSmall)
            state.fatalError?.let { ErrorCard(it) }
            DownloadCard(state, actions)
            BenchCard(state, actions)
            state.listening.forEach { (lang, items) ->
                val name = state.languages.firstOrNull { it.lang == lang }?.name ?: lang
                ListenCard(name, items, state, actions)
            }
            ShareCard(state, actions)
        }
    }
}

@Composable
private fun StepCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun DownloadCard(state: VoicesUiState, actions: VoicesActions) {
    StepCard("1. Download the voices") {
        state.languages.forEach {
            Text(
                "${it.name}: ${it.installed} of ${it.voices} voices ready",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("On this phone: ${state.storageMb} MB", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::downloadAll, enabled = !state.downloadBusy && !state.benchBusy) {
                Text("Download all (under 1 GB, Wi-Fi)")
            }
        }
        if (state.downloadLine.isNotEmpty()) Text(state.downloadLine, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::openTtsSettings) { Text("Phone voice packs") }
            OutlinedButton(
                onClick = actions::deleteModels,
                enabled = !state.downloadBusy && !state.benchBusy && state.storageMb > 0,
            ) { Text("Delete voices") }
        }
    }
}

@Composable
private fun BenchCard(state: VoicesUiState, actions: VoicesActions) {
    StepCard("2. Run the benchmark") {
        Text(
            "Keep the app open. The screen stays on until it finishes.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.benchBusy) {
            OutlinedButton(onClick = actions::cancelBenchmark) { Text("Cancel") }
        } else {
            Button(onClick = actions::startBenchmark, enabled = state.anyInstalled && !state.downloadBusy) {
                Text("Run benchmark")
            }
        }
        if (state.benchLine.isNotEmpty()) Text(state.benchLine, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ListenCard(name: String, items: List<ListenItem>, state: VoicesUiState, actions: VoicesActions) {
    StepCard("3. Listen and rate: $name") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.revealNames, onCheckedChange = actions::setRevealNames)
            Text("Show voice names", modifier = Modifier.padding(start = 8.dp))
        }
        items.forEach { item -> ListenRow(item, state, actions) }
    }
}

@Composable
private fun ListenRow(item: ListenItem, state: VoicesUiState, actions: VoicesActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.play(item.voiceId) }, enabled = item.hasSample) {
                Text(if (state.playingVoiceId == item.voiceId) "Stop" else "Play")
            }
            val name = if (state.revealNames) "Voice ${item.letter}: ${item.label}" else "Voice ${item.letter}"
            Text(if (item.hasSample) name else "$name (no sample yet)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ListeningStore.RATING_RANGE.forEach { value ->
                FilterChip(
                    selected = item.rating == value,
                    onClick = { actions.rate(item.voiceId, value) },
                    enabled = item.hasSample,
                    label = { Text("$value") },
                )
            }
        }
    }
}

@Composable
private fun ShareCard(state: VoicesUiState, actions: VoicesActions) {
    StepCard("4. Send the results") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::shareReport) { Text("Share report") }
            OutlinedButton(onClick = actions::shareLog) { Text("Share log file") }
        }
        if (state.report.isNotEmpty()) {
            Text(state.report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
