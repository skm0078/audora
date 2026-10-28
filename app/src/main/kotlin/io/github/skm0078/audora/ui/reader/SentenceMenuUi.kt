// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SentenceMenuUi.kt
// Purpose: The sentence menu with a line under each item saying what it is for (copy its words; Share as audio, a
//          picture or text), and the bar and notice of a highlight over several sentences (phone test issues 5, 6, 8).

package io.github.skm0078.audora.ui.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.HighlightConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.ui.theme.Dimens

/** One row of the sentence menu: what it does, and a line saying what for. */
private class MenuEntry(@StringRes val label: Int, @StringRes val hint: Int, val onClick: () -> Unit)

/**
 * The Share choices (ADR 2026-10-27-share-cards): "As audio" (the highlighted passage when the sentence has one, else
 * to the end of the paragraph), "As a picture" (a quote card) and the words as text, all of the same passage.
 */
private fun shareChoices(
    highlight: Highlight?,
    audio: () -> Unit,
    picture: () -> Unit,
    video: () -> Unit,
    text: () -> Unit,
) = listOf(
    MenuEntry(
        R.string.sentence_menu_share_clip,
        if (highlight != null) R.string.sentence_hint_share_highlight else R.string.sentence_hint_share_clip,
        audio,
    ),
    MenuEntry(R.string.share_as_picture, R.string.share_hint_picture, picture),
    MenuEntry(R.string.share_as_video, R.string.share_hint_video, video),
    MenuEntry(R.string.sentence_menu_share_text, R.string.sentence_hint_share_text, text),
)

/** What the menu's rows act through. */
private data class MenuSources(
    val actions: BookNotesActions,
    val reader: ReaderActions,
    val voice: VoiceNotesBinding,
    val lookUp: LookUpBinding,
    val words: WordsOut,
    val haptics: HapticFeedback,
)

/** A sentence's or passage's words, to the clipboard or to another app. Only the listener's own action sends them. */
private class WordsOut(private val context: Context) {
    fun copy(words: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), words))
        // From Android 13 the phone shows its own "Copied" note; before that, say it here.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.sentence_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(words: String) {
        val send = Intent(Intent.ACTION_SEND).setType(PLAIN_TEXT).putExtra(Intent.EXTRA_TEXT, words)
        try {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.sentence_menu_share_text)))
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(TAG, "No app to share text with: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "Audora.SentenceMenu"
        const val PLAIN_TEXT = "text/plain"
    }
}

/**
 * The rows for [unit]. On a highlighted sentence, Remove and Share act on the whole highlighted passage (the first,
 * when two merged into this sentence); a sentence with no words ("* * *") can never show a band, so it offers none.
 */
private fun MenuSources.sentenceMenu(unit: ReadingUnit, notes: NotesState, onShare: () -> Unit): List<MenuEntry> {
    val highlight = notes.highlightsOn(unit).firstOrNull()
    val canHighlight = highlight == null && unit.text.any(Char::isLetterOrDigit)
    fun closing(action: () -> Unit): () -> Unit = {
        actions.dismissMenu()
        action()
    }
    val record = closing { voice.askToRecord(unit) }
    val define = closing { lookUp.actions.open(unit) }
    val report = closing { reader.onReportSentence(unit) }
    // A highlighted passage's whole quote, else this sentence.
    val text = highlight?.quote?.takeIf { it.isNotBlank() } ?: unit.text
    val copyHint = if (highlight != null) R.string.sentence_hint_copy_highlight else R.string.sentence_hint_copy
    return listOfNotNull(
        MenuEntry(R.string.sentence_menu_bookmark, R.string.sentence_hint_bookmark) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            actions.bookmark(unit)
        },
        MenuEntry(R.string.sentence_menu_highlight, R.string.sentence_hint_highlight) { actions.toggleHighlight(unit) }
            .takeIf { canHighlight },
        MenuEntry(R.string.sentence_menu_highlight_range, R.string.sentence_hint_highlight_range) {
            actions.startRange(unit)
        }.takeIf { canHighlight },
        MenuEntry(R.string.sentence_menu_unhighlight, R.string.sentence_hint_unhighlight) {
            actions.toggleHighlight(unit)
        }.takeIf { highlight != null },
        MenuEntry(R.string.sentence_menu_voice_note, R.string.sentence_hint_voice_note, record)
            .takeIf { voice.enabled },
        MenuEntry(R.string.sentence_menu_look_up, R.string.sentence_hint_look_up, define)
            .takeIf { lookUp.enabled },
        MenuEntry(R.string.sentence_menu_copy, copyHint, closing { words.copy(text) }),
        MenuEntry(R.string.sentence_menu_share, R.string.sentence_hint_share, onShare),
        MenuEntry(R.string.sentence_menu_fix, R.string.sentence_hint_fix) { actions.fix(unit) },
        MenuEntry(R.string.report_title, R.string.sentence_hint_report, report),
    )
}

/** The rows behind "Share…", for the same passage the menu acts on: a highlight's whole quote, else [unit]. */
private fun MenuSources.shareMenu(unit: ReadingUnit, notes: NotesState): List<MenuEntry> {
    val highlight = notes.highlightsOn(unit).firstOrNull()
    val text = highlight?.quote?.takeIf { it.isNotBlank() } ?: unit.text
    val page = highlight?.anchor?.page ?: unit.page
    fun closing(action: () -> Unit): () -> Unit = {
        actions.dismissMenu()
        action()
    }
    return shareChoices(
        highlight,
        audio = closing { if (highlight != null) reader.onShareHighlight(highlight) else reader.onShareClip(unit) },
        picture = closing { reader.onSharePicture(text, page) },
        video = closing { reader.onShareVideo(text, page) },
        text = closing { words.share(text) },
    )
}

/** The menu a long-press on [unit] opens. */
@Composable
internal fun SentenceMenu(
    unit: ReadingUnit,
    notes: NotesState,
    actions: BookNotesActions,
    reader: ReaderActions,
    voice: VoiceNotesBinding,
    lookUp: LookUpBinding,
) {
    val context = LocalContext.current
    val words = remember(context) { WordsOut(context) }
    // "Share…" turns the same dialog into its choices, so a dialog never opens on top of another.
    var sharing by rememberSaveable(unit.page, unit.index) { mutableStateOf(false) }
    val sources = MenuSources(actions, reader, voice, lookUp, words, LocalHapticFeedback.current)
    if (sharing) {
        SentenceMenuDialog(
            sources.shareMenu(unit, notes),
            onDismiss = actions::dismissMenu,
            title = R.string.share_choices_title,
            note = R.string.share_choices_note,
        )
    } else {
        SentenceMenuDialog(sources.sentenceMenu(unit, notes) { sharing = true }, onDismiss = actions::dismissMenu)
    }
}

/**
 * While a passage's end is picked, its bar at the bottom of the reader (the box around it takes no touches, so the
 * page is still tapped through it); then why a passage was refused, once.
 */
@Composable
internal fun PassageOverlays(notes: NotesState, actions: BookNotesActions) {
    notes.rangeFrom?.let { from ->
        Box(Modifier.fillMaxSize()) {
            HighlightRangeBar(from, actions::cancelRange, Modifier.align(Alignment.BottomCenter))
        }
    }
    notes.rangeRefused?.let { RangeRefusedDialog(it, actions::dismissRangeRefusal) }
}

@Composable
private fun SentenceMenuDialog(
    rows: List<MenuEntry>,
    onDismiss: () -> Unit,
    @StringRes title: Int = R.string.sentence_menu_title,
    @StringRes note: Int? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            // Ten rows with their hints are taller than a phone's dialog: it scrolls.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { MenuRow(it.label, it.hint, it.onClick) }
                note?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.SpaceS),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

@Composable
private fun MenuRow(@StringRes label: Int, @StringRes hint: Int, onClick: () -> Unit) {
    // One button to TalkBack: the label, then the hint, read together.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown while the end of a highlighted passage is picked: where it starts, what to do, and Cancel. Polite live region,
 * so TalkBack says it once it appears.
 */
@Composable
fun HighlightRangeBar(from: ReadingUnit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceXs)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.highlight_range_prompt, from.page + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.password_cancel)) }
        }
    }
}

@Composable
private fun RangeRefusedDialog(why: RangeRefusal, onDismiss: () -> Unit) {
    val text = when (why) {
        RangeRefusal.TOO_LONG -> stringResource(R.string.highlight_range_too_long, HighlightConfig().maxPassagePages)
        RangeRefusal.PAGE_UNREADABLE -> stringResource(R.string.highlight_range_unreadable)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) } },
    )
}
