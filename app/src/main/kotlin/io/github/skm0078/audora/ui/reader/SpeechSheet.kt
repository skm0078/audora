// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SpeechSheet.kt
// Purpose: The player's speech sheet (ADR-062): speed, pitch (ADR-067), pace, reading to suit the book (ADR-056)
//          and this book's own settings first; what to read (ADR-035), sound, cast voices (item 7), study and focus
//          sounds each one row that opens its own sheet.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet
import io.github.skm0078.audora.ui.theme.SwitchOption
import io.github.skm0078.audora.ui.theme.namedSlider
import kotlin.math.roundToInt

/**
 * The slider only sends its speed when the finger lifts: every change restarts the sentence playing at the new
 * speed (ADR-033), so sending while dragging would restart it many times a second.
 *
 * What most listeners change comes first, each row its own control: speed, pitch, pace, reading to suit the book
 * (with what the book reads as, [readingIntent]) and this book's own settings. Pitch is sent when the finger lifts
 * too, though it restarts nothing (ADR-067): each change is saved by the service. The finer options wait one tap
 * away, each group in a sheet of its own over this one, so this sheet stays short (ADR-062).
 */
@Composable
fun SpeechSheet(
    speed: Float,
    pace: Pace,
    config: ReaderConfig,
    onSpeed: (Float) -> Unit,
    onPace: (Pace) -> Unit,
    onDismiss: () -> Unit,
    clausePauses: Boolean = true,
    onClausePauses: (Boolean) -> Unit = {},
    readingModes: ReadingModes = ReadingModes(),
    onReadingModes: (ReadingModes) -> Unit = {},
    voiceShaping: VoiceShaping = VoiceShaping(),
    onVoiceShaping: (VoiceShaping) -> Unit = {},
    ownSettings: Boolean = false,
    onOwnSettings: (Boolean) -> Unit = {},
    voices: BookVoices = BookVoices(),
    study: StudyControls = StudyControls(),
    focusSound: FocusSoundOptions = FocusSoundOptions(),
    onFocusSound: (FocusSoundOptions) -> Unit = {},
    blend: NarratorBlendState = NarratorBlendState(),
    bookInPlayer: Boolean = true,
    expressive: Boolean = true,
    readingIntent: ContentIntent? = null,
    pitch: Float = SpeechStyle.NORMAL_PITCH,
    onPitch: (Float) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf<SpeechPage?>(null) }
    OptionSheet(
        stringResource(R.string.speech_sheet_title),
        onDismiss,
        help = stringResource(R.string.speech_sheet_hint),
    ) {
        OptionGroup(title = null) {
            SpeedBlock(speed, config, onSpeed)
            OptionDivider()
            PitchBlock(pitch, config.speech, onPitch)
            OptionDivider()
            PaceBlock(pace, onPace)
            OptionDivider()
            ReadingStyleRow(voiceShaping.suitBook, readingIntent, bookInPlayer) {
                onVoiceShaping(voiceShaping.copy(suitBook = it))
            }
            OptionDivider()
            OwnSettingsRow(ownSettings, enabled = bookInPlayer || ownSettings, onOwnSettings)
        }
        // The voice is always one tap away (UI audit): this book's own while it keeps its own settings, else the
        // voice every book uses, the same choice as Settings → Voices.
        if (ownSettings) {
            BookVoicesSection(voices.rows, voices.chosen, stringResource(R.string.speech_book_voices), voices.onVoice)
        } else {
            BookVoicesSection(voices.rows, emptyMap(), stringResource(R.string.speech_voices)) { lang, id ->
                voices.rows.firstOrNull { it.lang == lang && it.id == id }?.let(voices.onDefault)
            }
        }
        PageLinks(readingModes, focusSound) { page = it }
    }
    val close = { page = null }
    when (page) {
        SpeechPage.READING -> OptionSheet(stringResource(R.string.reading_modes_title), close) {
            ReadingModesSection(readingModes, onReadingModes)
        }

        SpeechPage.SOUND -> OptionSheet(stringResource(R.string.speech_sound), close) {
            VoiceShapingSection(voiceShaping, config.voiceShaping, expressive, clausePauses, onClausePauses) {
                onVoiceShaping(it)
            }
            blend.ownSpeaker?.let { own ->
                NarratorBlendSection(voiceShaping, own, blend.neededBytes, onVoiceShaping)
            }
        }

        SpeechPage.CAST -> OptionSheet(stringResource(R.string.speech_cast), close) {
            CastVoicesSection(voices.rows, voiceShaping, onVoiceShaping)
        }

        SpeechPage.STUDY -> OptionSheet(stringResource(R.string.study_title), close) {
            StudySection(readingModes, study, onReadingModes)
        }

        SpeechPage.FOCUS -> OptionSheet(stringResource(R.string.focus_title), close) {
            FocusSoundSection(focusSound, onFocusSound)
        }

        null -> Unit
    }
}

/** The groups of finer options, each opened in its own sheet from a row of the speech sheet. */
private enum class SpeechPage { READING, SOUND, CAST, STUDY, FOCUS }

/** One row per group; What to read and Focus sound say what is set, so the common answers need no tap. */
@Composable
private fun PageLinks(modes: ReadingModes, focus: FocusSoundOptions, onOpen: (SpeechPage) -> Unit) {
    OptionGroup(title = null) {
        LinkOption(
            stringResource(R.string.reading_modes_title),
            onClick = { onOpen(SpeechPage.READING) },
            subtitle = readingSummary(modes),
        )
        OptionDivider()
        LinkOption(stringResource(R.string.speech_sound), onClick = { onOpen(SpeechPage.SOUND) })
        OptionDivider()
        LinkOption(stringResource(R.string.speech_cast), onClick = { onOpen(SpeechPage.CAST) })
        OptionDivider()
        LinkOption(stringResource(R.string.study_title), onClick = { onOpen(SpeechPage.STUDY) })
        OptionDivider()
        LinkOption(
            stringResource(R.string.focus_title),
            onClick = { onOpen(SpeechPage.FOCUS) },
            value = stringResource(focusSoundLabel(focus.sound)),
        )
    }
}

/** "Tables: Summary · Footnotes: Read", the two choices that change most books. */
@Composable
private fun readingSummary(modes: ReadingModes): String = listOf(
    stringResource(R.string.option_value_pair, stringResource(R.string.reading_tables), tableModeLabel(modes)),
    stringResource(R.string.option_value_pair, stringResource(R.string.reading_footnotes), footnoteModeLabel(modes)),
).joinToString(SUMMARY_SEPARATOR)

@Composable
private fun SpeedBlock(speed: Float, config: ReaderConfig, onSpeed: (Float) -> Unit) {
    val range = config.speech.minRate..config.speech.maxRate
    val step = config.speech.rateStep
    // Follows the player's speed, but shows the finger's position while dragging.
    var dragged by remember(speed) { mutableFloatStateOf(speed) }
    val haptics = LocalHapticFeedback.current
    val value = stringResource(R.string.speech_speed_value, speedLabel(config.nearestSpeed(dragged)))
    val title = stringResource(R.string.speech_speed_title)
    OptionBlock(title, value = value) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSpeed(config.nearestSpeed(speed - step)) }, enabled = speed > range.start) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.speech_sheet_slower))
            }
            Slider(
                value = dragged,
                onValueChange = {
                    // A tick at each step the thumb crosses, so the speed can be set by feel.
                    if (config.nearestSpeed(it) != config.nearestSpeed(dragged)) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    dragged = it
                },
                onValueChangeFinished = { onSpeed(config.nearestSpeed(dragged)) },
                valueRange = range,
                steps = stepsBetween(range, step),
                // TalkBack reads "Speed, 1.25×", not the thumb's share of the track.
                modifier = Modifier.weight(1f).namedSlider(title, value),
            )
            IconButton(onClick = { onSpeed(config.nearestSpeed(speed + step)) }, enabled = speed < range.endInclusive) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.speech_sheet_faster))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            config.speeds.forEach { preset ->
                FilterChip(
                    selected = preset == speed,
                    onClick = { onSpeed(preset) },
                    label = { Text(stringResource(R.string.speech_speed_value, speedLabel(preset))) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaceBlock(pace: Pace, onPace: (Pace) -> Unit) {
    OptionBlock(stringResource(R.string.speech_sheet_pace)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Pace.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == pace,
                    onClick = { onPace(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, Pace.entries.size),
                ) { Text(stringResource(paceLabel(option))) }
            }
        }
    }
}

/**
 * Plan item 4: speed, pace and voices become this book's own; off, it follows the settings of every book. The player
 * keeps them for the book it holds, so until this book has been played ([enabled] false) the switch waits for play.
 */
@Composable
private fun OwnSettingsRow(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SwitchOption(
        stringResource(R.string.speech_own_settings),
        on,
        onChange,
        subtitle = if (enabled) null else stringResource(R.string.speech_own_settings_play_first),
        help = stringResource(R.string.speech_own_settings_detail),
        enabled = enabled,
    )
}

private fun paceLabel(pace: Pace): Int = when (pace) {
    Pace.TIGHT -> R.string.speech_pace_tight
    Pace.NORMAL -> R.string.speech_pace_normal
    Pace.RELAXED -> R.string.speech_pace_relaxed
}

/** Material's Slider counts the stops between the ends, not the intervals. */
internal fun stepsBetween(range: ClosedFloatingPointRange<Float>, step: Float): Int =
    (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)

private const val SUMMARY_SEPARATOR = " · "

/**
 * The voices the open book can pick from (plan item 4), its own choices, and what a tap does: [onVoice] for this
 * book's own, [onDefault] for every book's while this one follows the usual settings.
 */
class BookVoices(
    val rows: List<VoiceRow> = emptyList(),
    val chosen: Map<Lang, VoiceId> = emptyMap(),
    val onVoice: (Lang, VoiceId) -> Unit = { _, _ -> },
    val onDefault: (VoiceRow) -> Unit = {},
)

/** Shown on the Focus sound row and in its choices. */
internal fun focusSoundLabel(sound: FocusSound): Int = when (sound) {
    FocusSound.OFF -> R.string.focus_off
    FocusSound.RAIN -> R.string.focus_rain
    FocusSound.CAFE -> R.string.focus_cafe
    FocusSound.BROWN_NOISE -> R.string.focus_brown_noise
}
