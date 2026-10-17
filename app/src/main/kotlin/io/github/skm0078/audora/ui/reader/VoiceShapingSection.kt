// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceShapingSection.kt
// Purpose: The speech sheet's sound section: even loudness, voice boost and pause trim (plan items 9, 10).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.VoiceShaping

/** Each change applies from the next sentence rendered; the sentence playing restarts like a speed change. */
@Composable
fun VoiceShapingSection(shaping: VoiceShaping, boostChoicesDb: List<Int>, onChange: (VoiceShaping) -> Unit) {
    Text(stringResource(R.string.speech_sound), style = MaterialTheme.typography.titleMedium)
    ShapingSwitch(
        stringResource(R.string.speech_even_loudness),
        stringResource(R.string.speech_even_loudness_detail),
        shaping.evenLoudness,
    ) { onChange(shaping.copy(evenLoudness = it)) }
    ShapingSwitch(
        stringResource(R.string.speech_trim_pauses),
        stringResource(R.string.speech_trim_pauses_detail),
        shaping.trimPauses,
    ) { onChange(shaping.copy(trimPauses = it)) }
    Text(stringResource(R.string.speech_boost), style = MaterialTheme.typography.bodyLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        boostChoicesDb.forEach { db ->
            FilterChip(
                selected = shaping.boostDb == db,
                onClick = { onChange(shaping.copy(boostDb = db)) },
                label = { Text(boostLabel(db)) },
            )
        }
    }
}

@Composable
private fun ShapingSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun boostLabel(db: Int): String =
    if (db == 0) stringResource(R.string.speech_boost_off) else stringResource(R.string.speech_boost_db, db)
