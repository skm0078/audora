// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderLookMenu.kt
// Purpose: The reader's overflow menu (page colours, follow-along, highlight, Focus view, bold word starts,
//          Up next) and the bold-start text styling.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** [text] with the starts of its English words bolded (plan item 77). */
internal fun boldWordStarts(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    boldWordStartRanges(text).forEach { range ->
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
    }
}

/**
 * The reader's overflow. Both views: the page colours (plan item 18), the Up next sheet (plan item 25), Save as
 * audiobook (ADR-034), search (plan item 16) and Read aloud to me (plan item 67). Text view only: follow-along, word
 * highlight and the highlight style (plan items 13 and 14), Focus view and bold word starts (plan item 77). The look
 * items are checkboxes and radio buttons, so TalkBack reads their state.
 */
@Composable
internal fun MoreMenu(
    textView: Boolean,
    look: ReadingLook,
    onLookChange: (ReadingLook) -> Unit,
    onOpenUpNext: (() -> Unit)?,
    onOpenTextLook: () -> Unit = {},
    onSearch: (() -> Unit)? = null,
    onSaveAudiobook: (() -> Unit)? = null,
    onReadAloudToMe: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.reader_more))
        }
        DropdownMenu(open, { open = false }, Modifier.widthIn(max = Dimens.MenuMaxWidth)) {
            ToneItems(look, onLookChange)
            if (textView) {
                LookItems(look, onLookChange)
                DropdownMenuItem(
                    text = { MenuText(R.string.reader_text_look, R.string.reader_hint_text_look) },
                    onClick = {
                        open = false
                        onOpenTextLook()
                    },
                )
            }
            onOpenUpNext?.let { openUpNext ->
                DropdownMenuItem(
                    text = { MenuText(R.string.up_next_title, R.string.reader_hint_up_next) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_queue), contentDescription = null) },
                    onClick = {
                        open = false
                        openUpNext()
                    },
                )
            }
            onSaveAudiobook?.let { save ->
                DropdownMenuItem(
                    text = { MenuText(R.string.library_export_audiobook, R.string.menu_hint_audiobook) },
                    onClick = {
                        open = false
                        save()
                    },
                )
            }
            onReadAloudToMe?.let { tutor ->
                DropdownMenuItem(
                    text = { MenuText(R.string.tutor_menu, R.string.reader_hint_tutor) },
                    onClick = {
                        open = false
                        tutor()
                    },
                )
            }
            onSearch?.let { search ->
                DropdownMenuItem(
                    text = { MenuText(R.string.search_action, R.string.reader_hint_search) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    onClick = {
                        open = false
                        search()
                    },
                )
            }
        }
    }
}

/** Page colours (plan item 18), in both views, so the page and its text agree. */
@Composable
private fun ToneItems(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    PageTone.entries.forEach { tone ->
        StyleItem(tone.label, selected = look.tone == tone) { onLookChange(look.copy(tone = tone)) }
    }
    HorizontalDivider()
}

private val PageTone.label: Int
    @StringRes get() = when (this) {
        PageTone.ORIGINAL -> R.string.reader_tone_original
        PageTone.SEPIA -> R.string.reader_tone_sepia
        PageTone.DARK -> R.string.reader_tone_dark
    }

@Composable
private fun LookItems(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    LookItem(R.string.reader_follow_along, R.string.reader_hint_follow_along, look.followAlong) {
        onLookChange(look.copy(followAlong = it))
    }
    LookItem(R.string.reader_word_highlight, R.string.reader_hint_word_highlight, look.wordHighlight) {
        onLookChange(look.copy(wordHighlight = it))
    }
    HighlightStyle.entries.forEach { style ->
        StyleItem(style.label, selected = look.highlight == style) { onLookChange(look.copy(highlight = style)) }
    }
    HorizontalDivider()
    LookItem(R.string.reader_focus_view, R.string.reader_hint_focus_view, look.focus) {
        onLookChange(look.copy(focus = it))
    }
    LookItem(R.string.reader_bold_word_starts, R.string.reader_hint_bold_word_starts, look.boldWordStarts) {
        onLookChange(look.copy(boldWordStarts = it))
    }
}

private val HighlightStyle.label: Int
    @StringRes get() = when (this) {
        HighlightStyle.FILL -> R.string.reader_highlight_fill
        HighlightStyle.UNDERLINE -> R.string.reader_highlight_underline
        HighlightStyle.BOLD -> R.string.reader_highlight_bold
    }

@Composable
private fun StyleItem(@StringRes label: Int, selected: Boolean, onSelect: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onSelect,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    )
}

@Composable
private fun LookItem(@StringRes label: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { MenuText(label, hint) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
        modifier = Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(checked)
        },
    )
}

/** A menu item's name with one plain line under it that says what it does. */
@Composable
private fun MenuText(@StringRes label: Int, @StringRes hint: Int) {
    Column {
        Text(stringResource(label))
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
