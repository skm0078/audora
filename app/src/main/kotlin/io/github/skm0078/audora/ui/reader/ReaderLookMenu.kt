// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderLookMenu.kt
// Purpose: The reader's overflow menu (ADR-062): one-line actions with icons, the look options behind "Page and
//          text"; and the bold-start text styling.

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * text opens every look option (plan items 13-15, 18, 77) in a sheet; then search (plan item 16), the Up next sheet
 * (plan item 25), Save as audiobook (ADR-034) and Read aloud to me (plan item 67). A null action is left out.
 */
@Composable
internal fun MoreMenu(
    onOpenPageText: () -> Unit,
    onOpenUpNext: (() -> Unit)?,
    onSearch: (() -> Unit)? = null,
    onSaveAudiobook: (() -> Unit)? = null,
    onReadAloudToMe: (() -> Unit)? = null,
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
            onSearch?.let { MenuAction(R.string.search_action, R.drawable.ic_search) { choose(it) } }
            onOpenUpNext?.let { MenuAction(R.string.up_next_title, R.drawable.ic_queue) { choose(it) } }
            if (onSaveAudiobook != null || onReadAloudToMe != null) HorizontalDivider()
            onSaveAudiobook?.let {
                MenuAction(R.string.library_export_audiobook, R.drawable.ic_download) { choose(it) }
            }
            onReadAloudToMe?.let { MenuAction(R.string.tutor_menu, R.drawable.ic_mic) { choose(it) } }
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
