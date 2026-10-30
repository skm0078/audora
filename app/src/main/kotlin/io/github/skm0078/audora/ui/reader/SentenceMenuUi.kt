// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SentenceMenuUi.kt
// Purpose: The sentence menu with a line under each item saying what it is for (copy its words; Share as audio, a
//          picture or text), its Share choices for one sentence or lines picked with "Choose more lines" (ADR
//          2026-10-29-share-credit-and-link), and the bar and notice of a passage picked over several sentences
//          (phone test issues 5, 6, 8).

package io.github.skm0078.audora.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.HighlightConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.Dimens

/** One row of the sentence menu: what it does, and a line saying what for. */
private class MenuEntry(@StringRes val label: Int, @StringRes val hint: Int, val onClick: () -> Unit)

/** What the menu's rows act through. */
private data class MenuSources(
    val actions: BookNotesActions,
    val reader: ReaderActions,
    val voice: VoiceNotesBinding,
    val lookUp: LookUpBinding,
    val words: WordsOut,
    val haptics: HapticFeedback,
)

/** A sentence's or passage's words, to the clipboard. Sharing them goes through [ReaderActions.onShareText]. */
private class WordsOut(private val context: Context) {
    fun copy(words: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), words))
        // From Android 13 the phone shows its own "Copied" note; before that, say it here.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.sentence_copied, Toast.LENGTH_SHORT).show()
        }
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
    // Share first: what a long-press is most often for.
    return listOfNotNull(
        MenuEntry(R.string.sentence_menu_share, R.string.sentence_hint_share, onShare),
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
        MenuEntry(R.string.sentence_menu_fix, R.string.sentence_hint_fix) { actions.fix(unit) },
        MenuEntry(R.string.report_title, R.string.sentence_hint_report, report),
    )
}

/**
 * What a Share menu acts on: [text] from zero-based [page], [audio] its clip (with [audioHint]); [unit] when it is one
 * sentence, which can grow into more lines or be cut from its drawn page.
 */
private class ShareLines(
    val text: String,
    val page: Int,
    @StringRes val audioHint: Int,
    val audio: () -> Unit,
    val unit: ReadingUnit? = null,
)

/** One sentence's lines: a highlight's whole quote when it is highlighted, else its own words. */
private fun MenuSources.linesOf(unit: ReadingUnit, notes: NotesState): ShareLines {
    val highlight = notes.highlightsOn(unit).firstOrNull()
    return ShareLines(
        text = highlight?.quote?.takeIf { it.isNotBlank() } ?: unit.text,
        page = highlight?.anchor?.page ?: unit.page,
        audioHint = highlight?.let { R.string.sentence_hint_share_highlight } ?: R.string.sentence_hint_share_clip,
        audio = { if (highlight != null) reader.onShareHighlight(highlight) else reader.onShareClip(unit) },
        unit = unit,
    )
}

/**
 * The Share choices (ADR 2026-10-27-share-cards, 2026-10-29-share-credit-and-link), most shared first: a picture (a
 * quote card), the words as text, a video, audio, part of the page (a sentence placed on a drawn page only: a text
 * book's has no page to cut), more lines, and the book itself. Every one carries the book, its author and
 * ListenLock's link. [close] runs before each, so a dialog never opens on top of this one.
 */
private fun MenuSources.shareMenu(lines: ShareLines, close: () -> Unit): List<MenuEntry> {
    fun closing(action: () -> Unit): () -> Unit = {
        close()
        action()
    }
    val (text, page, unit) = Triple(lines.text, lines.page, lines.unit)
    return listOfNotNull(
        MenuEntry(
            R.string.share_as_picture,
            R.string.share_hint_picture,
            closing { reader.onSharePicture(text, page) },
        ),
        MenuEntry(
            R.string.sentence_menu_share_text,
            R.string.sentence_hint_share_text,
            closing { reader.onShareText(text, page) },
        ),
        MenuEntry(R.string.share_as_video, R.string.share_hint_video, closing { reader.onShareVideo(text, page) }),
        MenuEntry(R.string.sentence_menu_share_clip, lines.audioHint, closing(lines.audio)),
        unit?.takeIf { !it.bounds.isNullOrEmpty() }?.let {
            MenuEntry(R.string.share_page_part, R.string.share_hint_page_part, closing { reader.onSharePagePart(it) })
        },
        // Picking the last line closes the menu itself and shows its bar.
        unit?.let { from ->
            MenuEntry(R.string.share_more_lines, R.string.share_hint_more_lines) { actions.startShareRange(from) }
        },
        MenuEntry(R.string.share_this_book, R.string.share_hint_this_book, closing(reader::onShareBook)),
    )
}

/** The menu a long-press on [unit] opens; the reader's Share button opens it on its Share choices. */
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
    var sharing by rememberSaveable(unit.page, unit.index) { mutableStateOf(notes.menuOnShare) }
    val sources = MenuSources(actions, reader, voice, lookUp, words, LocalHapticFeedback.current)
    if (sharing) {
        val lines = sources.linesOf(unit, notes)
        SentenceMenuDialog(
            sources.shareMenu(lines, actions::dismissMenu),
            onDismiss = actions::dismissMenu,
            title = R.string.share_choices_title,
            preview = lines.text,
            note = R.string.share_choices_note,
        )
    } else {
        SentenceMenuDialog(sources.sentenceMenu(unit, notes) { sharing = true }, onDismiss = actions::dismissMenu)
    }
}

/** The Share choices for lines picked with "Choose more lines": the same as one sentence's, as one passage. */
@Composable
internal fun SharedPassageMenu(passage: SharedPassage, actions: BookNotesActions, reader: ReaderActions) {
    val context = LocalContext.current
    val words = remember(context) { WordsOut(context) }
    val sources = MenuSources(
        actions,
        reader,
        VoiceNotesBinding.None,
        LookUpBinding.None,
        words,
        LocalHapticFeedback.current,
    )
    val lines = ShareLines(
        text = passage.quote,
        page = passage.first.page,
        audioHint = R.string.share_hint_lines_audio,
        audio = { reader.onSharePassage(passage.first, passage.last) },
    )
    SentenceMenuDialog(
        sources.shareMenu(lines, actions::dismissSharing),
        onDismiss = actions::dismissSharing,
        title = R.string.share_choices_title,
        preview = lines.text,
        note = R.string.share_choices_note,
    )
}

/**
 * While a passage's end is picked, its bar at the bottom of the reader (the box around it takes no touches, so the
 * page is still tapped through it); then why a passage was refused, once.
 */
@Composable
internal fun PassageOverlays(notes: NotesState, actions: BookNotesActions) {
    notes.rangeFrom?.let { from ->
        Box(Modifier.fillMaxSize()) {
            HighlightRangeBar(from, notes.rangeForShare, actions::cancelRange, Modifier.align(Alignment.BottomCenter))
        }
    }
    notes.rangeRefused?.let { RangeRefusedDialog(it, actions::dismissRangeRefusal) }
}

/** [preview]: the lines a Share menu acts on, quoted under its title, so the listener sees what will go. */
@Composable
private fun SentenceMenuDialog(
    rows: List<MenuEntry>,
    onDismiss: () -> Unit,
    @StringRes title: Int = R.string.sentence_menu_title,
    preview: String? = null,
    @StringRes note: Int? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            // Ten rows with their hints are taller than a phone's dialog: it scrolls.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                preview?.let {
                    Text(
                        "“${it.trim()}”",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = Dimens.SpaceS),
                    )
                }
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
 * Shown while the end of a passage is picked, to highlight or to share ([forShare]): where it starts, what to do, and
 * Cancel. Polite live region, so TalkBack says it once it appears.
 */
@Composable
fun HighlightRangeBar(from: ReadingUnit, forShare: Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val prompt = if (forShare) R.string.share_range_prompt else R.string.highlight_range_prompt
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceXs)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(prompt, from.page + 1),
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
        RangeRefusal.SHARE_TOO_LONG -> stringResource(R.string.share_range_too_long, HighlightConfig().maxPassagePages)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) } },
    )
}

// The quoted lines atop a Share menu: enough to recognise them, never a wall of text.
private const val PREVIEW_LINES = 3
