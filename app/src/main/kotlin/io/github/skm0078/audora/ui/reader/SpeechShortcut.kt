// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpeechShortcut.kt
// Purpose: The player's ways into the Voice and speed sheet (ADR 2026-10-29-voice-and-speed-one-level): the chip that
//          shows the speed and the voice reading, and three shortcuts that open the sheet at Voices, What to read and
//          Focus sound.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.Dimens

/** The shortcuts under the player's buttons, in order, each opening the sheet at its group. */
internal enum class SpeechShortcut(val spot: SpeechSpot, @DrawableRes val icon: Int) {
    VOICES(SpeechSpot.VOICE, R.drawable.ic_mic),
    WHAT_TO_READ(SpeechSpot.READING, R.drawable.ic_article),
    FOCUS_SOUND(SpeechSpot.FOCUS, R.drawable.ic_headphones),
}

/**
 * The Voice and speed chip: the tune icon, the speed and, under it, the voice reading now ([voiceName], left out when
 * none reads the book's language). TalkBack hears "Voice and speed" with the speed and voice as its state.
 */
@Composable
internal fun SpeechChip(speed: Float, voiceName: String?, enabled: Boolean, onClick: () -> Unit) {
    val title = stringResource(R.string.speech_sheet_title)
    val shownSpeed = speedLabel(speed)
    val state = voiceName?.let { stringResource(R.string.speech_chip_state, shownSpeed, it) }
        ?: stringResource(R.string.reader_speed, shownSpeed)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = title
            stateDescription = state
        },
    ) {
        Icon(painterResource(R.drawable.ic_tune), contentDescription = null)
        Spacer(Modifier.width(Dimens.SpaceXs))
        Column(Modifier.clearAndSetSemantics {}) {
            Text(stringResource(R.string.speech_speed_value, shownSpeed), style = MaterialTheme.typography.labelLarge)
            voiceName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = VOICE_NAME_MAX_WIDTH),
                )
            }
        }
    }
}

/** Voices, What to read and Focus sound, each one tap into its place in the sheet. Scrolls sideways if it must. */
@Composable
internal fun SpeechShortcuts(enabled: Boolean, onOpen: (SpeechSpot) -> Unit, modifier: Modifier = Modifier) {
    // Centred when the three fit; at large text sizes the row scrolls sideways instead of wrapping.
    Box(modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceM), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SpeechShortcut.entries.forEach { shortcut ->
                AssistChip(
                    onClick = { onOpen(shortcut.spot) },
                    enabled = enabled,
                    label = { Text(stringResource(shortcut.spot.title)) },
                    leadingIcon = {
                        Icon(
                            painterResource(shortcut.icon),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

/** The sheet as the player opens it, at [spot], with everything the player knows. */
@Composable
internal fun PlayerSpeechSheet(
    spot: SpeechSpot,
    playback: PlaybackSnapshot,
    config: ReaderConfig,
    actions: ReaderActions,
    voices: List<VoiceRow>,
    study: StudyControls,
    bookInPlayer: Boolean,
    bookLang: Lang?,
    onDismiss: () -> Unit,
) {
    SpeechSheet(
        speed = playback.speed,
        pace = playback.pace,
        config = config,
        onSpeed = actions::onSpeed,
        pitch = playback.pitch,
        onPitch = actions::onPitch,
        onPace = actions::onPace,
        clausePauses = playback.clausePauses,
        onClausePauses = actions::onClausePauses,
        readingModes = playback.readingModes,
        onReadingModes = actions::onReadingModes,
        voiceShaping = playback.voiceShaping,
        onVoiceShaping = actions::onVoiceShaping,
        ownSettings = playback.ownSettings,
        onOwnSettings = actions::onOwnSettings,
        voices = BookVoices(voices, playback.bookVoices, actions::onBookVoice, actions::onVoice, bookLang),
        study = study,
        focusSound = playback.focusSound,
        onFocusSound = actions::onFocusSound,
        blend = NarratorBlendState(playback.blendOwnSpeaker, playback.blendNeedsBytes),
        bookInPlayer = bookInPlayer,
        expressive = playback.voiceExpressive != false,
        readingIntent = playback.readingIntent,
        opensAt = spot,
        onDismiss = onDismiss,
    )
}

/** Long voice names end in "…" so the chip never pushes the play button off centre. */
private val VOICE_NAME_MAX_WIDTH = 88.dp
