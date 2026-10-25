// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/LanguagePackRow.kt
// Purpose: A language's one-tap voice download with plain progress, for Settings → Voices and the reader (ADR-056).

package io.github.skm0078.audora.ui.voices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.voices.LanguagePack
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.jobs.DownloadTimeLeft
import io.github.skm0078.audora.ui.theme.Dimens

/** "Hindi": what it downloads and its size, then the percentage and megabytes while it runs, then "Ready". */
@Composable
internal fun LanguagePackRow(pack: LanguagePack, onDownload: () -> Unit, onCancel: () -> Unit) {
    val language = stringResource(languageName(pack.lang))
    ListItem(
        headlineContent = { Text(language) },
        supportingContent = { PackStatus(pack, language) },
        trailingContent = {
            when {
                pack.ready -> Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                pack.downloading -> TextButton(onClick = onCancel) { Text(stringResource(R.string.voices_cancel)) }
                else -> Button(onClick = onDownload) { Text(stringResource(R.string.voice_pack_download)) }
            }
        },
    )
}

@Composable
internal fun PackStatus(pack: LanguagePack, language: String) {
    val voice = pack.voice
    val status = voice.status
    Column {
        when {
            pack.ready -> Announced(stringResource(R.string.voice_pack_ready, language))

            status is VoiceStatus.Downloading -> {
                val total = status.total.coerceAtLeast(1)
                val percent = (status.bytes * PERCENT / total).toInt()
                Text(
                    stringResource(
                        R.string.voice_pack_downloading,
                        percent,
                        megabytes(status.bytes),
                        megabytes(status.total),
                    ),
                )
                LinearProgressIndicator(
                    progress = { status.bytes.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
                )
                DownloadTimeLeft(status.bytes, status.total)
            }

            status == VoiceStatus.Queued -> Announced(stringResource(R.string.voice_pack_waiting))

            status is VoiceStatus.Failed -> Announced(
                stringResource(R.string.voice_pack_failed),
                MaterialTheme.colorScheme.error,
            )

            else -> Text(stringResource(R.string.voice_pack_offer, voice.label, megabytes(voice.sizeBytes)))
        }
    }
}

/** Outcomes are read out by TalkBack as they appear; progress ticks are not, so it is not flooded. */
@Composable
private fun Announced(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private const val PERCENT = 100
