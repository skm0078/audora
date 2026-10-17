// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderLookMenu.kt
// Purpose: The reader's overflow menu (follow-along, highlight, Focus view, bold word starts, Up next) and the
//          bold-start text styling.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.github.skm0078.audora.R

/** [text] with the starts of its English words bolded (plan item 77). */
internal fun boldWordStarts(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    boldWordStartRanges(text).forEach { range ->
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
    }
}

/**
 * The reader's overflow. Text view only: follow-along, word highlight and the highlight style (plan items 13 and 14),
 * Focus view and bold word starts (plan item 77). Always: the Up next sheet (plan item 25). The look items are
 * checkboxes and radio buttons, so TalkBack reads their state.
 */
@Composable
internal fun MoreMenu(
    textView: Boolean,
    look: ReadingLook,
    onLookChange: (ReadingLook) -> Unit,
    onOpenUpNext: (() -> Unit)?,
    onSearch: (() -> Unit)? = null,
) {
    if (!textView && onOpenUpNext == null && onSearch == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.reader_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (textView) LookItems(look, onLookChange)
            onOpenUpNext?.let { openUpNext ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.up_next_title)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_queue), contentDescription = null) },
                    onClick = {
                        open = false
                        openUpNext()
                    },
                )
            }
            onSearch?.let { search ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_action)) },
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

@Composable
private fun LookItems(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    LookItem(R.string.reader_follow_along, look.followAlong) { onLookChange(look.copy(followAlong = it)) }
    LookItem(R.string.reader_word_highlight, look.wordHighlight) { onLookChange(look.copy(wordHighlight = it)) }
    HighlightStyle.entries.forEach { style ->
        StyleItem(style.label, selected = look.highlight == style) { onLookChange(look.copy(highlight = style)) }
    }
    HorizontalDivider()
    LookItem(R.string.reader_focus_view, look.focus) { onLookChange(look.copy(focus = it)) }
    LookItem(R.string.reader_bold_word_starts, look.boldWordStarts) { onLookChange(look.copy(boldWordStarts = it)) }
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
        modifier = Modifier.semantics { role = Role.RadioButton },
    )
}

@Composable
private fun LookItem(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
        modifier = Modifier.semantics { role = Role.Checkbox },
    )
}
