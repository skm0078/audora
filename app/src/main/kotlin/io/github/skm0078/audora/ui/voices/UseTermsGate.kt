// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/UseTermsGate.kt
// Purpose: Asks the user to agree to a voice's use terms before its download or update starts (OpenRAIL-M,
//          2026-10-27 licence audit). Used by the Voices screen and the reader's language-pack card.

package io.github.skm0078.audora.ui.voices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceUseTerms
import io.github.skm0078.audora.ui.licences.LinkButton
import io.github.skm0078.audora.ui.licences.rememberLinkOpener

/**
 * Sends a download through [download] at once, unless the voice has use terms ([VoiceRow.useTerms]): then it waits in
 * [pending] until the user agrees ([agree]) or turns it down ([dismiss]). Asked on every download and update, as
 * they are rare; nothing is stored about it.
 */
internal class UseTermsGate(private val download: (VoiceRow) -> Unit) {
    var pending: VoiceRow? by mutableStateOf(null)
        private set

    fun request(row: VoiceRow) {
        val terms = row.useTerms
        if (terms == null) {
            download(row)
        } else {
            AudoraLog.i(TAG, "Use terms ${terms.key} shown for voice=${row.id.name}")
            pending = row
        }
    }

    fun agree() {
        val row = pending ?: return
        pending = null
        AudoraLog.i(TAG, "Use terms agreed for voice=${row.id.name}")
        download(row)
    }

    fun dismiss() {
        pending?.let { AudoraLog.i(TAG, "Use terms declined for voice=${it.id.name}") }
        pending = null
    }

    private companion object {
        const val TAG = "UI.UseTerms"
    }
}

/** A gate whose downloads always go to the latest [download]; show its [UseTermsDialog] next to the button. */
@Composable
internal fun rememberUseTermsGate(download: (VoiceRow) -> Unit): UseTermsGate {
    val latest by rememberUpdatedState(download)
    return remember { UseTermsGate { latest(it) } }
}

/** [actions] with its downloads and updates sent through [gate]. */
internal fun termsGated(actions: VoicesActions, gate: UseTermsGate): VoicesActions = object : VoicesActions by actions {
    override fun onDownload(row: VoiceRow) = gate.request(row)
}

/** The pending voice's terms in plain words, the full licence one tap away; nothing while none is pending. */
@Composable
internal fun UseTermsDialog(gate: UseTermsGate) {
    val row = gate.pending ?: return
    val terms = row.useTerms ?: return
    val openLink = rememberLinkOpener()
    AlertDialog(
        onDismissRequest = gate::dismiss,
        title = { Text(stringResource(R.string.use_terms_title, row.label)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(termsText(terms)))
                LinkButton(R.string.use_terms_read_licence) { openLink(terms.licenceUrl) }
            }
        },
        confirmButton = { TextButton(onClick = gate::agree) { Text(stringResource(R.string.use_terms_agree)) } },
        dismissButton = { TextButton(onClick = gate::dismiss) { Text(stringResource(R.string.use_terms_cancel)) } },
    )
}

private fun termsText(terms: VoiceUseTerms): Int = when (terms) {
    VoiceUseTerms.OPENRAIL_M -> R.string.use_terms_openrail_m
}
