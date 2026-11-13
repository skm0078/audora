// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderLookMenu.kt
// Purpose: The reader's overflow menu (ADR-062): one-line actions with icons, the look options behind "Page and
//          text" and the Text size sheet; and the bold-start text styling.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
 * The reader's overflow: actions only, each one line with an icon, so the menu scans at a glance (ADR-062). Page and
 * text opens every look option (plan items 13-15, 18, 77) in a sheet, and Text size the one people look for most on
 * its own; then search (plan item 16), the Up next sheet
 * (plan item 25), Save as audiobook (ADR-034), Read aloud to me (plan item 67) and Gestures and tips, the one-time card
 * shown again (ADR 2026-10-30-player-line-actions). A null action is left out.
 */
@Composable
private fun MoreMenu(
    onOpenPageText: () -> Unit,
    onOpenUpNext: (() -> Unit)?,
    onOpenTextSize: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onSaveAudiobook: (() -> Unit)? = null,
    onReadAloudToMe: (() -> Unit)? = null,
    onShowTips: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val choose = { action: () -> Unit ->
        open = false
        action()
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.reader_more))
        }
        DropdownMenu(open, { open = false }) {
            MenuAction(R.string.page_text_title, R.drawable.ic_view_text) { choose(onOpenPageText) }
            onOpenTextSize?.let { MenuAction(R.string.text_size_title, R.drawable.ic_format_size) { choose(it) } }
            onSearch?.let { MenuAction(R.string.search_action, R.drawable.ic_search) { choose(it) } }
            onOpenUpNext?.let { MenuAction(R.string.up_next_title, R.drawable.ic_queue) { choose(it) } }
            if (onSaveAudiobook != null || onReadAloudToMe != null) HorizontalDivider()
            onSaveAudiobook?.let {
                MenuAction(R.string.library_export_audiobook, R.drawable.ic_download) { choose(it) }
            }
            onReadAloudToMe?.let { MenuAction(R.string.tutor_menu, R.drawable.ic_mic) { choose(it) } }
            onShowTips?.let {
                HorizontalDivider()
                MenuAction(R.string.gesture_tips_menu, R.drawable.ic_info) { choose(it) }
            }
        }
    }
}

@Composable
private fun MenuAction(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

/** The reader's ⋮ with its actions wired: the look sheets from [sheets], the rest from the reader. */
@Composable
internal fun ReaderMoreMenu(
    view: ReaderView,
    actions: ReaderActions,
    sheets: LookSheets,
    onOpenUpNext: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onReadAloudToMe: (() -> Unit)?,
    onShowTips: () -> Unit,
) {
    MoreMenu(
        onOpenPageText = sheets::openPageText,
        onOpenUpNext = onOpenUpNext,
        onOpenTextSize = { sheets.openTextSize(view, actions) },
        onSearch = onSearch,
        onSaveAudiobook = actions::onSaveAudiobook,
        onReadAloudToMe = onReadAloudToMe,
        onShowTips = onShowTips,
    )
}

/**
 * Which look sheet is open from ⋮: Page and text (every option) or Text size alone. Kept across rotation, as a sheet
 * the listener opened should still be there after turning the phone.
 */
@Stable
internal class LookSheets(private val pageText: MutableState<Boolean>, private val textSize: MutableState<Boolean>) {
    fun openPageText() {
        pageText.value = true
    }

    /** The size is the text view's, so the reader turns to it: that is where the change shows. */
    fun openTextSize(view: ReaderView, actions: ReaderActions) {
        if (view == ReaderView.PAGE) actions.onViewChanged(ReaderView.TEXT)
        textSize.value = true
    }

    @Composable
    fun Shown(look: ReadingLook, onLookChange: (ReadingLook) -> Unit, textView: Boolean) {
        if (pageText.value) PageTextSheet(look, onLookChange, textView) { pageText.value = false }
        if (textSize.value) TextSizeSheet(look, onLookChange) { textSize.value = false }
    }
}

@Composable
internal fun rememberLookSheets(): LookSheets {
    val pageText = rememberSaveable { mutableStateOf(false) }
    val textSize = rememberSaveable { mutableStateOf(false) }
    return remember(pageText, textSize) { LookSheets(pageText, textSize) }
}
